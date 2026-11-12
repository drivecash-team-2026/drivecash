<#
  본 앱(드라이브캐시)을 Virtual Console 로 실제 주행처럼 시험한다.

  순서: 초기화 → 앱 실행 → 정차 화면 캡처 → 가속(단계적) → 순항 → 스팟 구간 진입 → 급제동 → 감속 → Park → 리포트 캡처
  결과: %TEMP%\drivecash-app\events.txt, app-log.txt(Logcat 태그 DriveCash), shot-*.png

  사용법:  powershell -ExecutionPolicy Bypass -File tools\app-scenario.ps1
           옵션: -CruiseSec 40 (순항 시간) -SkipHarsh (급제동 생략)
#>
param(
    [string]$Apk = (Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"),
    [string]$OutDir = (Join-Path $env:TEMP "drivecash-app"),
    [int]$CruiseSec = 40,
    [switch]$SkipHarsh
)

$ErrorActionPreference = "Continue"
Start-Transcript -Path (Join-Path $env:TEMP "app-scenario-transcript.txt") -Force | Out-Null
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$base = "http://localhost:8088"
$pkg = "com.drivecash.app"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$events = Join-Path $OutDir "events.txt"
Remove-Item $events -ErrorAction SilentlyContinue
Get-ChildItem $OutDir -Filter "shot-*.png" | Remove-Item -ErrorAction SilentlyContinue

function Ev($m) { Add-Content $events ("{0} {1}" -f (Get-Date -Format "HH:mm:ss.fff"), $m) }
function Put($path) { $r = curl.exe -s -m 8 -X PUT "$base$path" -w "|http=%{http_code}"; if ($r -notmatch 'http=200') { Ev "PUT failed $path -> $($r.Substring([Math]::Max(0,$r.Length-60)))" } }
function Speed($kph, $acc = 0, $brake = 0) { Put "/vehicle/controls?speed=$kph&accelerator=$acc&brake=$brake&steering=0" }
function Shot($name) {
    $f = Join-Path $OutDir "shot-$name.png"
    cmd /c "`"$adb`" exec-out screencap -p > `"$f`""
    Ev "shot $name"
}

$hb = Start-Job -ScriptBlock {
    while ($true) { curl.exe -s -m 3 -X POST "http://localhost:8088/browser/session?client=drivecash-test" | Out-Null; Start-Sleep -Seconds 1 }
}

try {
    & $adb forward tcp:8088 tcp:8088 | Out-Null
    & $adb install -g -r (Resolve-Path $Apk) | Out-Null
    & $adb shell pm clear $pkg | Out-Null
    # pm clear 는 런타임 권한도 취소하므로 다시 부여한다.
    # CAR_POWERTRAIN 은 일반 앱이 받을 수 없는 권한이라 부여하지 않는다. cmd 로 감싸 stderr 가 스크립트를 멈추지 않게 한다.
    foreach ($perm in "pleos.car.permission.FUSED_LOCATION", "android.car.permission.CAR_SPEED") {
        cmd /c "`"$adb`" shell pm grant $pkg $perm >nul 2>&1"
    }
    Start-Sleep -Seconds 2

    Put "/vehicle/gear?gear=Park"; Speed 0 0 100
    Put "/vehicle/odometer?traveled_distance_km=200"
    & $adb emu geo fix -122.084 37.422 | Out-Null
    & $adb logcat -c | Out-Null

    Ev "app start"
    & $adb shell am start -n "$pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 12
    Shot "01-park"

    Ev "gear Drive"; Put "/vehicle/gear?gear=Drive"; Speed 0 0 0
    Start-Sleep -Seconds 3

    Ev "accelerate 0 to 60 kph"
    foreach ($v in 10, 20, 30, 40, 50, 60) { Speed $v 30 0; Start-Sleep -Milliseconds 1500 }
    Shot "02-drive"

    Ev "cruise 60 kph ${CruiseSec}s"
    $half = [int]($CruiseSec / 2)
    Start-Sleep -Seconds $half
    Ev "enter zone A (geo fix)"
    & $adb emu geo fix -122.0800 37.4300 | Out-Null
    Start-Sleep -Seconds ($CruiseSec - $half)
    Shot "03-bonus"

    if (-not $SkipHarsh) {
        Ev "hard brake to 20 kph"; Speed 20 0 80; Start-Sleep -Seconds 3
    }
    Ev "decelerate to stop"
    foreach ($v in 15, 10, 5, 0) { Speed $v 0 40; Start-Sleep -Milliseconds 1500 }
    Speed 0 0 100; Start-Sleep -Seconds 2

    Ev "gear Park"; Put "/vehicle/gear?gear=Park"
    Start-Sleep -Seconds 4
    Shot "04-report"

    $raw = Join-Path $OutDir "raw.bin"
    # 태그 필터(-s)는 이 환경에서 빈 결과를 냈다. 전체를 받아 앱 태그 줄만 남긴다.
    cmd /c "`"$adb`" logcat -d -v time > `"$raw`" 2>nul"
    $all = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($raw))
    $txt = (($all -split "`r?`n") | Where-Object { $_ -match '/DriveCash\(' }) -join "`n"
    [System.IO.File]::WriteAllText((Join-Path $OutDir "app-log.txt"), $txt, (New-Object System.Text.UTF8Encoding($false)))
    Ev "DONE"
    Write-Output "결과: $OutDir"
}
finally {
    Stop-Job $hb -ErrorAction SilentlyContinue
    Remove-Job $hb -Force -ErrorAction SilentlyContinue
}
