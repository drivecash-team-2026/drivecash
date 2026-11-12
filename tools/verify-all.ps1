<#
  남은 검증 항목을 단계별로 실행하고 요약을 %TEMP%\drivecash-verify\summary.txt 에 모은다.

  사용법:  powershell -ExecutionPolicy Bypass -File tools\verify-all.ps1 -Phase 1
           Phase 1: -g 없이 설치(CAR_SPEED 권한 동작), 내비 경로 요청 후 제한속도·내비 위치 관찰
           Phase 2: 오디오를 켜고 에뮬레이터를 재시작해 TTS 재생 이벤트 확인
           Phase 3: 바코드 화면 캡처 + 주행 시나리오 반복(ANR 재현 여부)  -Repeat 2
  전제: Phase 1 은 에뮬레이터가 부팅된 상태여야 한다. Phase 2 는 에뮬레이터를 직접 다시 시작한다.
#>
param(
    [ValidateSet(1, 2, 3)][int]$Phase = 1,
    [int]$Repeat = 2
)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$out = Join-Path $env:TEMP "drivecash-verify"
New-Item -ItemType Directory -Force $out | Out-Null
$summary = Join-Path $out "summary.txt"
$pkg = "com.drivecash.app"

function Note($m) { Add-Content $summary ("{0} {1}" -f (Get-Date -Format "HH:mm:ss"), $m) -Encoding UTF8 }
function Adb($a) { cmd /c "`"$adb`" $a >nul 2>&1" }
function AdbOut($a, $file) { cmd /c "`"$adb`" $a > `"$file`" 2>&1" }
function Put($path) { curl.exe -s -m 8 -X PUT "http://localhost:8088$path" | Out-Null }
function Shot($name) { cmd /c "`"$adb`" exec-out screencap -p > `"$out\$name.png`""; Note ("캡처 $name " + (Get-Item "$out\$name.png").Length + "B") }
function AppLogs($tagPattern) {
    $raw = Join-Path $out "logcat.bin"
    cmd /c "`"$adb`" logcat -d -v time > `"$raw`" 2>nul"
    $all = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($raw)) -split "`r?`n"
    $all | Where-Object { $_ -match $tagPattern } | ForEach-Object { ($_ -replace '^\d\d-\d\d ', '') -replace '[IWD]/[A-Za-z]+\(\s*\d+\): ', '' }
}
function AnrCount() {
    $raw = Join-Path $out "logcat_anr.bin"
    cmd /c "`"$adb`" logcat -d -v time > `"$raw`" 2>nul"
    @([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($raw)) -split "`r?`n" | Where-Object { $_ -match 'ANR in com.drivecash' }).Count
}
function Build() {
    $bat = Join-Path $env:TEMP "build_app.bat"
    $b = Join-Path $env:TEMP "build_out.txt"
    Remove-Item $b -ErrorAction SilentlyContinue
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $bat -WindowStyle Hidden -Wait
    $res = (Get-Content $b | Where-Object { $_ -match '^e: |BUILD' }) -join " ; "
    Note "빌드: $res"
    return ($res -match 'BUILD SUCCESSFUL')
}
function StartHeartbeat() {
    Start-Job -ScriptBlock { while ($true) { curl.exe -s -m 3 -X POST "http://localhost:8088/browser/session?client=drivecash-verify" | Out-Null; Start-Sleep -Seconds 1 } }
}
function WaitBoot($maxSec) {
    $o = Join-Path $out "boot.txt"
    $deadline = (Get-Date).AddSeconds($maxSec)
    while ((Get-Date) -lt $deadline) {
        AdbOut "shell getprop sys.boot_completed" $o
        if (((Get-Content $o -ErrorAction SilentlyContinue) -join "").Trim() -eq "1") { return $true }
        Start-Sleep -Seconds 5
    }
    return $false
}

Note "===== Phase $Phase 시작"
if (-not (Build)) { Note "빌드 실패로 중단"; exit 1 }

# Phase 2 는 에뮬레이터를 직접 다시 시작한다. 나머지는 켜져 있어야 하므로, 꺼져 있으면 시작하고 부팅·안정화를 기다린다.
if ($Phase -ne 2) {
    $devOut = Join-Path $out "devices.txt"
    AdbOut "devices" $devOut
    if (-not ((Get-Content $devOut -ErrorAction SilentlyContinue) -match 'emulator-\d+\s+device')) {
        Note "에뮬레이터가 꺼져 있어 시작합니다 (오디오 없음)"
        $p = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "start-emulator.ps1") -WindowStyle Hidden -PassThru
        $p.WaitForExit(); Note "start-emulator 종료 코드 $($p.ExitCode)"
        if (-not (WaitBoot 120)) { Note "부팅 확인 실패로 중단"; exit 1 }
        Start-Sleep -Seconds 90
        Note "부팅 후 안정화 90초 대기 완료"
    }
}

if ($Phase -eq 1) {
    Adb "forward tcp:8088 tcp:8088"
    # 일부러 -g 를 쓰지 않는다. 런타임 권한이 없을 때 AAOS 속도 읽기가 되는지 본다.
    Adb "install -r `"$apk`""
    Adb "shell pm clear $pkg"
    Adb "shell pm grant $pkg pleos.car.permission.FUSED_LOCATION"
    AdbOut "shell dumpsys package $pkg" (Join-Path $out "pkg.txt")
    $perm = Get-Content (Join-Path $out "pkg.txt") | Where-Object { $_ -match 'CAR_SPEED|FUSED_LOCATION|CAR_POWERTRAIN|CAR_DRIVE|TTS_SERVICE|NAVI_ROUTE' } | ForEach-Object { $_.Trim() }
    Note "---- 권한 상태 (설치 시 -g 없음)"
    $perm | ForEach-Object { Note $_ }

    $hb = StartHeartbeat
    try {
        Start-Sleep -Seconds 2
        Put "/vehicle/gear?gear=Drive"; Put "/vehicle/controls?speed=50&accelerator=20&brake=0&steering=0"
        Adb "logcat -c"
        Adb "shell am start -n $pkg/.MainActivity --ez autorun true --ez route true --ei listen 70"
        Note "검증 화면 실행(경로 요청 포함, 관찰 70초)"
        Start-Sleep -Seconds 20
        Adb "emu geo fix 127.0276 37.4979"; Note "geo fix -> 강남역 부근"
        Start-Sleep -Seconds 15
        Adb "emu geo fix 127.0300 37.5000"; Note "geo fix -> 북동쪽 약 300m"
        Start-Sleep -Seconds 15
        Adb "emu geo fix 127.0400 37.5100"; Note "geo fix -> 더 먼 위치"
        Start-Sleep -Seconds 35
        Shot "p1-navi-state"
        $lines = AppLogs '/DriveCashVerify\('
        Note "---- 검증 로그 (총 $($lines.Count)줄)"
        $lines | Where-Object { $_ -match 'V-02\] AAOS (속도 변경|PERF|GEAR|읽기)|V-06|V-13|V-07\] 위치 #(1|10|20|40)\b|권한|예외|SecurityException' } |
            ForEach-Object { if ($_.Length -gt 170) { $_.Substring(0, 170) } else { $_ } } | Select-Object -First 70 | ForEach-Object { Note $_ }
        $limits = ($lines | Where-Object { $_ -match '제한속도: (\d+)' } | ForEach-Object { [regex]::Match($_, '제한속도: (\d+)').Groups[1].Value } | Sort-Object -Unique) -join ","
        Note "제한속도 값 종류: [$limits]"
        $navi = ($lines | Where-Object { $_ -match '내비 위치: (.+)$' } | ForEach-Object { [regex]::Match($_, '내비 위치: (.+)$').Groups[1].Value } | Select-Object -Unique) -join " | "
        Note "내비 위치 종류: [$navi]"
    } finally { Stop-Job $hb -ErrorAction SilentlyContinue; Remove-Job $hb -Force -ErrorAction SilentlyContinue }
}

if ($Phase -eq 2) {
    Get-Process emulator, qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 6
    Get-ChildItem (Join-Path $env:USERPROFILE ".android\avd\Pleos_Connect_v2_1.avd") -Filter "*.lock" -ErrorAction SilentlyContinue | ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
    $p = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "start-emulator.ps1"), "-Audio" -WindowStyle Hidden -PassThru
    $p.WaitForExit(); Note "에뮬레이터(-Audio) 시작 종료 코드 $($p.ExitCode)"
    Start-Sleep -Seconds 90
    Adb "install -g -r `"$apk`""
    Adb "logcat -c"
    Adb "shell am start -n $pkg/.MainActivity --ez autorun true --ei listen 30"
    Start-Sleep -Seconds 50
    $lines = AppLogs '/(DriveCashVerify|DriveCash)\('
    Note "---- TTS 관련 로그"
    $lines | Where-Object { $_ -match 'V-08|TTS' } | ForEach-Object { if ($_.Length -gt 170) { $_.Substring(0, 170) } else { $_ } } | Select-Object -First 30 | ForEach-Object { Note $_ }
}

if ($Phase -eq 3) {
    Adb "install -g -r `"$apk`""
    Adb "shell am force-stop $pkg"
    Adb "shell am start -n $pkg/.MainActivity"
    Start-Sleep -Seconds 20
    # 시연 포인트를 넉넉히 지급하고 첫 교환 항목을 눌러 바코드를 띄운다. (좌표는 2560x1440 화면 기준)
    Adb "shell input tap 1181 1244"; Start-Sleep -Seconds 3
    Adb "shell input tap 2398 309"; Start-Sleep -Seconds 3
    Shot "p3-barcode"
    Adb "shell input tap 1919 800"; Start-Sleep -Seconds 2
    for ($i = 1; $i -le $Repeat; $i++) {
        Note "---- 주행 시나리오 반복 $i/$Repeat"
        Start-Sleep -Seconds 60
        Adb "logcat -c"
        $p = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "app-scenario.ps1") -WindowStyle Hidden -PassThru
        $p.WaitForExit(); $anr = AnrCount
        $lines = AppLogs '/DriveCash\('
        $report = $lines | Where-Object { $_ -match '\[REPORT\]' } | Select-Object -Last 1
        Note "반복 $i : 시나리오 종료 코드 $($p.ExitCode), ANR $anr, 앱 로그 $($lines.Count)줄"
        if ($report) { Note $report } else { Note "리포트 없음 (주행이 끝까지 처리되지 않음)" }
        $tts = @($lines | Where-Object { $_ -match '\[TTS\]' })
        Note ("TTS 로그 {0}줄 (안내 {1}, 재생 시작 {2}, 재생 완료 {3})" -f $tts.Count, @($tts | Where-Object { $_ -match '안내:' }).Count, @($tts | Where-Object { $_ -match '재생 시작' }).Count, @($tts | Where-Object { $_ -match '재생 완료' }).Count)
    }
}
Note "===== Phase $Phase 완료"
