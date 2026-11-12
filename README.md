# 현대 드라이브캐시 (DriveCash)

차량용 앱 개발 공모전(Pleos Connect)에 제출하는 시연용 앱입니다. 운전하는 동안 안전운전 점수와 주행 거리로 포인트를 쌓고, 정차하면 출석체크·일일 미션·포인트 교환을 쓸 수 있습니다.

> 이 저장소의 포인트 기준, 사용처, 교환권은 **시연용 가정값과 가짜 데이터**입니다. 실제 서비스와 연동되지 않습니다.

## 주요 기능

| 구분 | 내용 |
|---|---|
| 주행 모드 (기어 D) | 조작 버튼 없이 주행 거리, 적립 포인트, 안전운전 점수, 속도만 표시합니다. 보너스는 음성(TTS)으로 안내합니다. |
| 정차 모드 (기어 P) | 주행 리포트, 출석체크(연속 출석 보너스), 일일 미션, 포인트 교환(바코드 표시), 가까운 사용처를 보여 줍니다. |
| 안전운전 점수 | 속도 변화율로 급가속(2.8 m/s² 이상)과 급제동(-3.0 m/s² 이하)을 판정하고 감점합니다. 점수가 90 미만이면 안전운전 보너스와 미션 보상이 지급되지 않습니다. |
| 스팟 보너스 | 급가속·급제동 없이 0.5 km를 달리면 보너스, 지정한 스팟 구간에 진입하면 구간 보너스를 지급합니다. |

## 사용한 Pleos SDK와 표준 API

| SDK / API | 용도 |
|---|---|
| Vehicle SDK 2.3.2 | 주행거리(Odometer), 기어 상태 |
| FusedLocation SDK 1.1.3 | 현재 위치, 스팟 구간 근접 알림(Proximity Alert) |
| NaviHelper SDK 3.0.5 | 현재 도로 제한속도 콜백 연동 |
| TextToSpeech SDK 3.3.1 | 주행 시작과 보너스 음성 안내 |
| Android Automotive 표준 속성 | 차량 속도(`PERF_VEHICLE_SPEED`), 기어(`GEAR_SELECTION`) |

Vehicle SDK에는 속도·가속도 API가 없어 속도는 Android Automotive 표준 속성으로 읽고, 급가속·급제동은 속도 변화율로 앱이 직접 계산합니다. 내비게이션은 안전운전 점수를 제공하지 않아 점수도 앱이 직접 산출합니다.

## 빌드와 실행

필요한 것: JDK 17, Android SDK (API 34/35), [Pleos Connect 에뮬레이터 v2.1.0](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator).

```
gradlew.bat assembleDebug
adb install -g app/build/outputs/apk/debug/app-debug.apk
```

처음 실행하면 속도와 위치 권한을 허용하는 대화상자가 두 번 나옵니다. 차량 값은 에뮬레이터의 Virtual Console로 바꿀 수 있고, 시연 재생 스크립트가 `tools/`에 있습니다.

| 스크립트 | 용도 |
|---|---|
| `tools/start-emulator.ps1` | 에뮬레이터 시작과 부팅 대기 |
| `tools/demo-run.ps1` | 출석부터 주행, 리포트, 교환까지 시연을 자동 재생 |
| `tools/app-scenario.ps1` | 주행 시나리오 한 번 재생과 로그 수집 |

## 확인한 것과 확인하지 못한 것

| 항목 | 상태 |
|---|---|
| 속도, 기어, 위치, 근접 알림, 출석·주행·리포트·교환 흐름 | Pleos Connect 에뮬레이터에서 동작 확인 |
| 앱 화면이 내비게이션에 가려져도 주행 기록 유지 | 에뮬레이터에서 확인 (포그라운드 서비스 사용) |
| TTS 음성 | SDK 호출과 시작·완료 이벤트까지 확인. **에뮬레이터에 오디오 출력 장치가 없어 소리는 확인하지 못했습니다.** |
| 현재 도로 제한속도 | 콜백은 연동했으나 에뮬레이터에서는 값이 계속 0이라 **실차에서 확인이 필요합니다.** 값이 0이면 과속 판정을 하지 않습니다. |
| 마이현대, 현대샵, 블루핸즈 연동 | 없음. 시연용 모의 교환입니다. 가까운 사용처 목록도 가짜 좌표의 예시입니다. |
| Pleos App Market 심사 | 포그라운드 서비스, 주행 중 화면 유지(DO) 선언, Android Automotive 권한이 허용되는지는 확인하지 못했습니다. |
| 세로 화면 | 공식 에뮬레이터(가로 2560x1440) 기준으로 만들었고 세로 화면은 시험하지 않았습니다. |

포인트와 안전운전 기준값은 기획서에 수치가 없어 임의로 정한 값이며 `domain/RewardPolicy.kt` 한 곳에 모여 있습니다.

## 시연 보조 기능

정차 화면 하단의 **"시연용 포인트 +10,000P"** 는 교환 화면을 보여 주기 위한 시연 보조 기능입니다. 실제 서비스에는 없어야 하는 기능이며, 화면에 "시연용"이라고 표시됩니다.

## 구조

```
app/src/main/java/com/drivecash/app/
  domain/    주행/정차 전환, 거리·점수 계산, 포인트 정책 (Android 의존 없음)
  data/      포인트 저장, 차량·위치 신호 수신
  ui/        Compose 화면
  tts/       음성 안내
  verify/    SDK 동작을 확인하는 개발자 검증 화면
tools/       에뮬레이터 제어와 시연 재생 스크립트
docs/        개발 기록 (결정, SDK 검증 현황)
```

개발 과정의 결정과 SDK 검증 결과는 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 있습니다.