<#
  시연 영상용 화면 녹화. 에뮬레이터 안에서 `screenrecord` 로 녹화하므로 PC의 다른 창이 영상에 들어가지 않는다.
  녹화하는 동안 주행 시나리오(tools\app-scenario.ps1)를 재생한다.

  사용법:  powershell -ExecutionPolicy Bypass -File tools\record-demo.ps1
  결과:    %TEMP%\drivecash-demo\demo.mp4

  주의(미검증): 이 에뮬레이터 이미지에서 screenrecord 가 동작하는지, 해상도 2560x1440 을 지원하는지는
  아직 확인하지 않았다. 실패하면 -Size 1280x720 처럼 해상도를 낮춘다. screenrecord 는 1회 최대 180초이다.
  녹화 전에 정차 화면에 개인정보(프로필 아이콘의 글자 등)가 나오지 않는지 확인한다.
#>
param(
    [string]$Size = "1920x1080",
    [int]$BitRateMbps = 12,
    [int]$LimitSec = 175
)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$out = Join-Path $env:TEMP "drivecash-demo"
New-Item -ItemType Directory -Force $out | Out-Null
$remote = "/sdcard/drivecash-demo.mp4"
$local = Join-Path $out "demo.mp4"
Remove-Item $local -ErrorAction SilentlyContinue

# 녹화를 장치 안에서 백그라운드로 시작
$bits = $BitRateMbps * 1000000
Start-Process -FilePath $adb -ArgumentList "shell", "screenrecord", "--size", $Size, "--bit-rate", $bits, "--time-limit", $LimitSec, $remote -WindowStyle Hidden
Start-Sleep -Seconds 3

# 시나리오 재생 (이 스크립트가 끝날 때까지 기다림)
$p = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "app-scenario.ps1") -WindowStyle Hidden -PassThru -Wait

# screenrecord 가 파일을 마무리할 시간을 준다
Start-Sleep -Seconds 5
cmd /c "`"$adb`" shell pkill -SIGINT screenrecord >nul 2>&1"
Start-Sleep -Seconds 4
cmd /c "`"$adb`" pull $remote `"$local`" >nul 2>&1"
cmd /c "`"$adb`" shell rm $remote >nul 2>&1"

if (Test-Path $local) {
    Write-Output ("영상 저장: {0} ({1:N1} MB), 시나리오 종료 코드 {2}" -f $local, ((Get-Item $local).Length / 1MB), $p.ExitCode)
} else {
    Write-Output "영상 파일을 가져오지 못했습니다. screenrecord 지원 여부를 확인하세요."
}
