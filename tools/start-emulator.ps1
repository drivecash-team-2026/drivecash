<#
  Pleos Connect 에뮬레이터를 시작하고 부팅이 끝날 때까지 기다린다.

  사용법:  powershell -ExecutionPolicy Bypass -File tools\start-emulator.ps1
           옵션: -Audio  (오디오 켜기. TTS 시험용)  -TimeoutSec 300

  주의: -no-audio 로 띄우면 TTS 재생 장치가 없어 speak 이후 이벤트가 오지 않았다. (docs/DEVELOPMENT.md V-08)
  부팅 판정은 `adb shell getprop sys.boot_completed` 가 1 이 되는 것이다. adb 호출은 파일로 받아서 읽는다.
#>
param(
    [string]$Avd = "Pleos_Connect_v2_1",
    [switch]$Audio,
    [int]$TimeoutSec = 300
)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emu = Join-Path $sdk "emulator\emulator.exe"
$env:ANDROID_SDK_ROOT = $sdk

$emuArgs = @("-avd", $Avd, "-no-snapshot", "-no-boot-anim", "-gpu", "host")
if (-not $Audio) { $emuArgs += "-no-audio" }

# 에뮬레이터가 예고 없이 종료되는 일이 있었다. 종료 원인을 남기기 위해 출력을 파일로 보관한다.
$logDir = Join-Path $env:TEMP "drivecash-emulator"
New-Item -ItemType Directory -Force $logDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outLog = Join-Path $logDir "emulator-$stamp.out.log"
$errLog = Join-Path $logDir "emulator-$stamp.err.log"
Start-Process -FilePath $emu -ArgumentList $emuArgs -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog
Write-Output "에뮬레이터 시작: $Avd (로그: $logDir)"

# adb 호출은 응답이 없을 수 있어서 별도 프로세스로 실행하고 5초 안에 끝나지 않으면 버린다.
function BootCompleted() {
    $tmp = Join-Path $logDir "boot-probe.txt"
    Remove-Item $tmp -ErrorAction SilentlyContinue
    $p = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$adb`" shell getprop sys.boot_completed > `"$tmp`" 2>&1" -WindowStyle Hidden -PassThru
    if (-not $p.WaitForExit(8000)) { try { $p.Kill() } catch {} ; return $false }
    $v = ((Get-Content $tmp -ErrorAction SilentlyContinue) -join "").Trim()
    return ($v -eq "1")
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    if (BootCompleted) { Write-Output "부팅 완료"; exit 0 }
    Start-Sleep -Seconds 4
}
Write-Output "부팅 대기 시간 초과 (${TimeoutSec}초)"
exit 1
