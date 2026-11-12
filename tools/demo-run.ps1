<#
  시연 영상용 자동 재생. 앱을 직접 누르지 않아도 출석 -> 주행(100km/h) -> 난폭 주행 1회(감점) -> 안전 주행 1회(보너스·미션)
  -> 리포트 -> 포인트 교환 2건(바코드) 순서로 보여준다. 소요 시간은 약 2분 45초이다.

  사용법 (단계가 둘로 나뉜다):
    1) 녹화를 시작하기 전에:  powershell -ExecutionPolicy Bypass -File tools\demo-run.ps1 -Phase Prepare
       데이터를 0P로 초기화하고 위치·기어를 원래대로 돌린 뒤 정차 화면을 띄운다. 끝나면 화면을 확인한다.
    2) PC 화면 녹화(Win+Alt+R)를 시작한 뒤:  powershell -ExecutionPolicy Bypass -File tools\demo-run.ps1 -Phase Run
       옵션 -AlsoRecord: 에뮬레이터 안 screenrecord 로 소리 없는 영상도 따로 남긴다(보험). 결과 %TEMP%\drivecash-demo\demo-silent.mp4

  전제: 에뮬레이터가 켜져 있고 부팅 후 2분 이상 지났으며, 앱이 설치되어 권한(속도·위치)이 허용된 상태.
  좌표는 2560x1440 화면 기준이다.

  속도 설계: 거리 적립을 빠르게 하려고 100km/h 로 달린다(1km 에 약 36초). 가속은 1.3초에 10km/h 씩 올려
  급가속(2.8m/s² 이상) 판정이 걸리지 않게 한다. 급제동은 100 -> 65km/h 로 한 번만 넣는다(2번이면 점수가 90 아래로
  내려가 일일 미션이 달성되지 않는다). 12m/s² 를 넘는 변화는 신호 점프로 무시되므로 35km/h 이내로 떨어뜨린다.
#>
param(
    [ValidateSet("Prepare", "Run")][string]$Phase = "Run",
    [switch]$AlsoRecord,
    [switch]$Debug   # 단계별 화면을 %TEMP%\drivecash-demo\steps 에 저장한다(리허설 확인용). 영상용 실행에서는 끈다(장당 1.4초 걸려 영상이 늘어진다).
)

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$base = "http://localhost:8088"
$pkg = "com.drivecash.app"
$out = Join-Path $env:TEMP "drivecash-demo"
New-Item -ItemType Directory -Force $out | Out-Null
$log = Join-Path $out "demo-run-log.txt"

function Note($m) { Add-Content $log ("{0} {1}" -f (Get-Date -Format "HH:mm:ss"), $m) -Encoding UTF8 }
function Adb($a) { cmd /c "`"$adb`" $a >nul 2>&1" }
function Tap($x, $y, $name) { Adb "shell input tap $x $y"; Note "탭 $name ($x,$y)" }
$steps = Join-Path $out "steps"
New-Item -ItemType Directory -Force $steps | Out-Null
$stepNo = 0
$script:UseLookup = $false   # true 로 바꾸면 버튼을 화면에서 찾아 실제 좌표를 로그에 남긴다(좌표 재측정용)
# 화면을 저장한다(영상과 따로, 단계별 확인용).
function Snap($name) {
    if (-not $Debug) { return }
    $script:stepNo++
    $file = Join-Path $steps ("{0:D2}-{1}.png" -f $script:stepNo, $name)
    cmd /c "`"$adb`" exec-out screencap -p > `"$file`""
}
# 화면의 텍스트 노드를 읽어 [text, x1, y1, x2, y2] 목록으로 돌려준다.
function UiNodes() {
    cmd /c "`"$adb`" shell uiautomator dump /sdcard/ui.xml >nul 2>&1"
    $tmp = Join-Path $out "ui.xml"
    cmd /c "`"$adb`" pull /sdcard/ui.xml `"$tmp`" >nul 2>&1"
    if (-not (Test-Path $tmp)) { return @() }
    $xml = [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8)
    $list = @()
    foreach ($m in [regex]::Matches($xml, '<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
        if ($m.Groups[1].Value -ne '') {
            $list += ,@([System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value), [int]$m.Groups[2].Value, [int]$m.Groups[3].Value, [int]$m.Groups[4].Value, [int]$m.Groups[5].Value)
        }
    }
    return $list
}
# 텍스트가 일치하는 요소를 찾아 가운데를 누른다. 못 찾으면 timeoutSec 동안 다시 찾고, 그래도 없으면 fallback 좌표를 누른다.
# index: 같은 텍스트가 여러 개일 때 위에서부터 몇 번째인지(0부터).
function TapText($pattern, $name, $fallbackX, $fallbackY, $index = 0, $timeoutSec = 0) {
    # 이름 조회는 한 번에 약 4~5초가 걸린다. 영상이 늘어지지 않도록 기본은 조회 없이 좌표를 바로 누른다.
    # $UseLookup 이 켜져 있을 때만(리허설에서 좌표를 다시 측정할 때) 조회한다.
    if (-not $script:UseLookup) {
        Adb "shell input tap $fallbackX $fallbackY"; Note "탭 $name (좌표 $fallbackX,$fallbackY)"; return
    }
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        $hits = @(UiNodes | Where-Object { $_[0] -match $pattern } | Sort-Object { $_[2] })
        if ($hits.Count -gt $index) {
            $h = $hits[$index]; $x = [int](($h[1] + $h[3]) / 2); $y = [int](($h[2] + $h[4]) / 2)
            Adb "shell input tap $x $y"; Note "탭 $name (이름으로 찾음 $x,$y)"; return
        }
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $deadline)
    Adb "shell input tap $fallbackX $fallbackY"; Note "탭 $name (못 찾아 좌표 $fallbackX,$fallbackY 사용)"
}function Put($path) { curl.exe -s -m 8 -X PUT "$base$path" | Out-Null }
function Speed($kph, $acc = 0, $brake = 0) { Put "/vehicle/controls?speed=$kph&accelerator=$acc&brake=$brake&steering=0" }
function Ramp($from, $to, $step, $ms, $acc = 30, $brake = 0) {
    $v = $from
    while ($v -ne $to) {
        if ($to -gt $from) { $v = [Math]::Min($v + $step, $to) } else { $v = [Math]::Max($v - $step, $to) }
        Speed $v $acc $brake; Start-Sleep -Milliseconds $ms
    }
}

# 화면 좌표 (정차 화면, 2560x1440)
$ATTEND = @(1117, 571)        # 출석하고 포인트 받기
$DEMO_POINTS = @(1181, 1244)  # 시연용 포인트 +10,000P
$RESET = @(1411, 1244)        # 시연 데이터 초기화
$REPORT_OK1 = @(1991, 898)    # 주행 1 리포트 확인 (거리 포인트만 있는 짧은 창)
$REPORT_OK2 = @(1991, 990)    # 주행 2 리포트 확인 (보너스 줄이 많은 긴 창)
$REWARD_FUEL = @(2398, 309)   # 주유 할인권 5,000원
$REWARD_EV = @(2398, 440)     # EV 충전 할인권 3,000원
$VOUCHER_CLOSE = @(2011, 869) # 바코드 창 닫기 (확대한 창 기준)

& $adb forward tcp:8088 tcp:8088 | Out-Null
$hb = Start-Job -ScriptBlock { while ($true) { curl.exe -s -m 3 -X POST "http://localhost:8088/browser/session?client=drivecash-demo" | Out-Null; Start-Sleep -Seconds 1 } }
try {
    Start-Sleep -Seconds 2

    if ($Phase -eq "Prepare") {
        Remove-Item $log -ErrorAction SilentlyContinue
        Note "준비 시작"
        Put "/vehicle/gear?gear=Park"; Speed 0 0 100
        Put "/vehicle/odometer?traveled_distance_km=200"
        & $adb emu geo fix -122.084 37.422 | Out-Null
        Adb "shell am force-stop $pkg"
        Adb "shell am start -n $pkg/.MainActivity"
        Start-Sleep -Seconds 12
        Tap $RESET[0] $RESET[1] "시연 데이터 초기화"
        Start-Sleep -Seconds 3
        Note "준비 완료. 화면에 0P가 보이는지 확인한 뒤 PC 화면 녹화를 시작하세요."
        Write-Output "준비 완료. 화면이 0P인지 확인하고, 녹화(Win+Alt+R)를 시작한 뒤 -Phase Run 을 실행하세요."
        return
    }

    # ---------------- Run (초기화는 하지 않는다. Prepare 에서 이미 했다)
    Get-ChildItem $steps -Filter "*.png" -ErrorAction SilentlyContinue | Remove-Item -ErrorAction SilentlyContinue; $script:stepNo = 0
    Note "재생 시작"
    $rec = $null
    if ($AlsoRecord) {
        $remote = "/sdcard/drivecash-demo.mp4"
        $rec = Start-Process -FilePath $adb -ArgumentList "shell", "screenrecord", "--size", "1920x1080", "--bit-rate", 8000000, "--time-limit", 175, $remote -WindowStyle Hidden -PassThru
        Note "screenrecord 시작"
    }
    Put "/vehicle/gear?gear=Park"; Speed 0 0 100
    & $adb emu geo fix -122.084 37.422 | Out-Null   # 위치를 스팟 구간 밖으로(구간 안에 남아 있으면 '진입'이 일어나지 않는다)
    Adb "shell am start -n $pkg/.MainActivity"
    Start-Sleep -Seconds 4                        # 장면 1: 정차 화면(0P, 미션, 가까운 사용처)

    Snap "정차화면"
    TapText '출석하고 포인트 받기' "출석체크" $ATTEND[0] $ATTEND[1]
    Start-Sleep -Seconds 2                        # 장면 2: 출석 +50P
    Snap "출석후"

    # ===== 주행 1: 난폭 운전. 급가속 2 + 급제동 2 -> 점수 76, 스팟·안전 보너스 없음, 미션 미달성
    Note "주행 1 시작 (기어 D)"; Put "/vehicle/gear?gear=Drive"; Speed 0 0 0
    Start-Sleep -Seconds 3                        # 음성 "주행을 시작합니다"
    Snap "주행1-시작"
    Note "급가속 1 (0 -> 25)";   Speed 25 90 0;  Start-Sleep -Seconds 4; Snap "주행1-급가속1"
    Note "급제동 1 (25 -> 0)";   Speed 0 0 90;   Start-Sleep -Seconds 4
    Note "급가속 2 (0 -> 30)";   Speed 30 90 0;  Start-Sleep -Seconds 4
    Note "급제동 2 (30 -> 0)";   Speed 0 0 90;   Start-Sleep -Seconds 4
    Note "주행 1 종료 (기어 P)"; Put "/vehicle/gear?gear=Park"
    Start-Sleep -Seconds 6                        # 장면: 난폭 주행 리포트(점수 하락, 보너스 없음) 읽을 시간
    Snap "주행1-리포트"
    TapText '^확인$' "리포트 확인(주행1)" $REPORT_OK1[0] $REPORT_OK1[1]
    Start-Sleep -Seconds 2
    Snap "주행1-리포트닫은뒤"

    # ===== 주행 2: 안전 운전. 완만한 가속 + 100km/h 순항 + 구간 A·B + 급제동 없음 -> 점수 100, 모든 보너스, 미션 달성
    Note "주행 2 시작 (기어 D)"; Put "/vehicle/gear?gear=Drive"; Speed 0 0 0
    Start-Sleep -Seconds 3
    Snap "주행2-시작"
    Note "완만한 가속 0 -> 100"
    Ramp 0 100 10 1300                            # 약 13초 (급가속이 걸리지 않는 완만한 가속)
    Note "순항 100"
    Start-Sleep -Seconds 5; Snap "주행2-순항"
    Note "스팟 구간 A 진입"; & $adb emu geo fix -122.0800 37.4300 | Out-Null
    Start-Sleep -Seconds 6                        # 구간 A 알림 + 음성
    Snap "주행2-구간A"
    Note "스팟 구간 B 진입"; & $adb emu geo fix -122.0700 37.4400 | Out-Null
    Start-Sleep -Seconds 13                       # 구간 B 알림 + 음성, 스팟 보너스 +30P (누적 거리 1km 이상 여유 확보)
    Ramp 100 0 10 1300 0 40                       # 완만하게 감속해서 정차 (약 13초)
    Speed 0 0 100; Start-Sleep -Seconds 1
    Note "주행 2 종료 (기어 P)"; Put "/vehicle/gear?gear=Park"
    Start-Sleep -Seconds 7                        # 장면: 안전 주행 리포트(점수 100, 안전 보너스, 미션) 읽을 시간
    Snap "주행2-리포트"
    TapText '^확인$' "리포트 확인(주행2)" $REPORT_OK2[0] $REPORT_OK2[1]
    Start-Sleep -Seconds 2                        # 최근 주행 2건, 미션 달성을 보여 준다
    Snap "주행2-리포트닫은뒤"

    TapText '시연용 포인트' "시연용 포인트" $DEMO_POINTS[0] $DEMO_POINTS[1]
    Start-Sleep -Milliseconds 2500                      # '시연용 포인트 +10,000P 지급' 안내문이 화면에 보이도록 충분히 기다린다
    Snap "시연포인트지급후"
    TapText '^5,000P$' "주유 할인권 교환" $REWARD_FUEL[0] $REWARD_FUEL[1]
    Start-Sleep -Seconds 4                        # 바코드(시연용 코드)
    Snap "교환1-바코드"
    TapText '^닫기$' "바코드 닫기" $VOUCHER_CLOSE[0] $VOUCHER_CLOSE[1]
    Start-Sleep -Milliseconds 1500
    Snap "교환1-닫은뒤"
    TapText '^3,000P$' "EV 충전 할인권 교환" $REWARD_EV[0] $REWARD_EV[1]
    Start-Sleep -Milliseconds 3500
    Snap "교환2-바코드"
    TapText '^닫기$' "바코드 닫기" $VOUCHER_CLOSE[0] $VOUCHER_CLOSE[1]
    Start-Sleep -Seconds 4                        # 교환 내역 2건, 마무리
    Snap "마지막"
    if ($rec) {
        cmd /c "`"$adb`" shell pkill -SIGINT screenrecord >nul 2>&1"
        Start-Sleep -Seconds 5
        $local = Join-Path $out "demo-silent.mp4"
        Remove-Item $local -ErrorAction SilentlyContinue
        cmd /c "`"$adb`" pull /sdcard/drivecash-demo.mp4 `"$local`" >nul 2>&1"
        cmd /c "`"$adb`" shell rm /sdcard/drivecash-demo.mp4 >nul 2>&1"
        if (Test-Path $local) { Note ("소리 없는 영상 저장 {0:N1}MB" -f ((Get-Item $local).Length / 1MB)) } else { Note "소리 없는 영상 가져오기 실패" }
    }
    Note "재생 끝"
    Write-Output "재생 끝. 이제 PC 화면 녹화를 멈추세요. (로그: $log)"
}
finally {
    Speed 0 0 100
    Stop-Job $hb -ErrorAction SilentlyContinue
    Remove-Job $hb -Force -ErrorAction SilentlyContinue
}