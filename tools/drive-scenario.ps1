<#
  Virtual Console(에뮬레이터 안의 웹 앱, 포트 8088)로 주행 시나리오를 재생하고,
  앱의 Logcat(태그 DriveCashVerify) 결과를 UTF-8 파일로 저장한다.

  사용법:  powershell -ExecutionPolicy Bypass -File tools\drive-scenario.ps1
           옵션: -Apk <경로> (기본값: app\build\outputs\apk\debug\app-debug.apk)  -OutDir <경로>

  전제: 에뮬레이터가 부팅되어 있고(tools\start-emulator.ps1), APK가 빌드되어 있다(gradlew assembleDebug).
  Virtual Console은 브라우저 하트비트(POST /browser/session?client=...)를 1초마다 받아야 활성화된다.
  이 엔드포인트들은 공식 문서가 아니라 콘솔 페이지에서 알아낸 것이다. (docs/DEVELOPMENT.md V-12)
#>
param(
    [string]$Apk = (Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"),
    [string]$OutDir = (Join-Path $env:TEMP "drivecash-scenario")
)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$base = "http://localhost:8088"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$events = Join-Path $OutDir "events.txt"
Remove-Item $events -ErrorAction SilentlyContinue

function Ev($m) { Add-Content $events ("{0} {1}" -f (Get-Date -Format "HH:mm:ss.fff"), $m) }
function Put($path) {
    $r = curl.exe -s -m 8 -X PUT "$base$path" -w "|http=%{http_code}"
    Ev ("PUT $path -> " + $r.Substring([Math]::Max(0, $r.Length - 9)))
}

# 하트비트를 백그라운드 잡으로 유지한다.
$hb = Start-Job -ScriptBlock {
    while ($true) { curl.exe -s -m 3 -X POST "http://localhost:8088/browser/session?client=drivecash-test" | Out-Null; Start-Sleep -Seconds 1 }
}

try {
    & $adb forward tcp:8088 tcp:8088 | Out-Null
    & $adb install -g -r (Resolve-Path $Apk) | Out-Null
    Start-Sleep -Seconds 2

    Put "/vehicle/gear?gear=Park"
    Put "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0"
    Put "/vehicle/odometer?traveled_distance_km=100"

    & $adb shell am force-stop com.drivecash.app | Out-Null
    & $adb logcat -c | Out-Null
    Ev "앱 시작 (autorun)"
    & $adb shell am start -n com.drivecash.app/.MainActivity --ez autorun true | Out-Null
    Start-Sleep -Seconds 9

    Ev "S1 Drive";            Put "/vehicle/gear?gear=Drive"; Start-Sleep -Seconds 2
    Ev "S2 60km/h";           Put "/vehicle/controls?speed=60&accelerator=20&brake=0&steering=0"; Start-Sleep -Seconds 4
    Ev "S3 주행거리 100.5";   Put "/vehicle/odometer?traveled_distance_km=100.5"; Start-Sleep -Seconds 4
    Ev "S4 주행거리 101";     Put "/vehicle/odometer?traveled_distance_km=101"; Start-Sleep -Seconds 3
    Ev "S5 급제동 20km/h";    Put "/vehicle/controls?speed=20&accelerator=0&brake=80&steering=0"; Start-Sleep -Seconds 4
    Ev "S6 정차 + Park";      Put "/vehicle/controls?speed=0&accelerator=0&brake=100&steering=0"; Put "/vehicle/gear?gear=Park"; Start-Sleep -Seconds 4

    # logcat 은 UTF-8 바이트로 받아서 저장한다. (콘솔 코드 페이지 때문에 한글이 깨지는 것을 막는다)
    $raw = Join-Path $OutDir "raw.bin"
    cmd /c "`"$adb`" logcat -d -v time -s DriveCashVerify:I > `"$raw`" 2>nul"
    $txt = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($raw))
    [System.IO.File]::WriteAllText((Join-Path $OutDir "log.txt"), $txt, (New-Object System.Text.UTF8Encoding($false)))
    Ev "완료"
    Write-Output "결과: $OutDir\events.txt, $OutDir\log.txt"
}
finally {
    Stop-Job $hb -ErrorAction SilentlyContinue
    Remove-Job $hb -Force -ErrorAction SilentlyContinue
}
