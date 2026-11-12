<#
  에뮬레이터 시작 -> 본 앱 주행 시나리오 -> 로그 수집을 한 번에 실행하고, 요약을 한 파일에 모은다.

  사용법:  powershell -ExecutionPolicy Bypass -File tools\run-all.ps1
  결과:    %TEMP%\drivecash-run\summary.txt  (+ events.txt, app-log.txt, shot-*.png)

  에뮬레이터가 이미 떠 있으면 그대로 쓴다. 꺼져 있으면 -Audio 로 시작한다.
#>
param([switch]$NoAudio)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$out = Join-Path $env:TEMP "drivecash-run"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null
$summary = Join-Path $out "summary.txt"
function Note($m) { Add-Content $summary ("{0} {1}" -f (Get-Date -Format "HH:mm:ss"), $m) -Encoding UTF8 }

Note "시작"
$devices = (& $adb devices 2>&1) -join " "
if ($devices -notmatch 'emulator-\d+\s+device') {
    Note "에뮬레이터 없음 -> 시작"
    $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "start-emulator.ps1"))
    if (-not $NoAudio) { $args += "-Audio" }
    $p = Start-Process -FilePath "powershell.exe" -ArgumentList $args -WindowStyle Hidden -PassThru -Wait
    Note "에뮬레이터 시작 스크립트 종료 코드 $($p.ExitCode)"
    Start-Sleep -Seconds 30
} else {
    Note "에뮬레이터 이미 실행 중"
}

$scenarioOut = Join-Path $env:TEMP "drivecash-app"
Remove-Item $scenarioOut -Recurse -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "app-scenario.ps1") -WindowStyle Hidden -PassThru -Wait
Note "시나리오 종료 코드 $($p.ExitCode)"

if (Test-Path $scenarioOut) { Copy-Item "$scenarioOut\*" $out -Force -ErrorAction SilentlyContinue }

# logcat 은 UTF-8 바이트로 받아 앱 태그 줄만 남긴다.
$raw = Join-Path $out "logcat.bin"
cmd /c "`"$adb`" logcat -d -v time > `"$raw`" 2>nul"
$all = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($raw)) -split "`r?`n"
$app = $all | Where-Object { $_ -match '/DriveCash\(' } | ForEach-Object { ($_ -replace '^\d\d-\d\d ', '') -replace '[IWD]/DriveCash\(\s*\d+\): ', '' }
[System.IO.File]::WriteAllLines((Join-Path $out "app-log.txt"), $app, (New-Object System.Text.UTF8Encoding($false)))

$anr = @($all | Where-Object { $_ -match 'ANR in com.drivecash' }).Count
Note "ANR: $anr, 앱 로그 줄: $($app.Count)"
Note "---- 핵심 이벤트"
$app | Where-Object { $_ -match 'HARSH|IGNORED|BONUS|REPORT|POINTS|MODE|ZONE\] .*ENTER|ACCEL\] -' } |
    ForEach-Object { if ($_.Length -gt 170) { $_.Substring(0, 170) } else { $_ } } |
    ForEach-Object { Add-Content $summary $_ -Encoding UTF8 }
Note "완료"
