# 현대 드라이브캐시 (DriveCash) 개발 문서

이 문서는 프로젝트의 기준 문서입니다. 결정, 조사 결과, 변경 사항을 여기에 기록합니다.
실제 코드 변경 내역은 git 커밋이 담당하고, 이 문서는 요약과 결정 이유를 담당합니다.

현재 버전: **v1.0.4**

## 변경 이력

새 항목은 맨 위에 추가합니다.

| 버전 | 날짜 | 변경 내용 | 이유 |
|---|---|---|---|
| v1.0.4 | 2026-11-12 | **주행 리포트와 교환권(바코드) 대화상자를 키움.** 기본 폭 제한을 풀고(`usePlatformDefaultWidth=false`) 창 폭을 720~860dp로 넓히고 글씨를 확대(리포트 제목 28→40sp, 항목 18→26sp, 값 20→30sp, 총 적립 40sp, 바코드 높이 72→150dp, 코드 26→46sp, 안내문 13→22sp, 확인·닫기 버튼 30sp). 시연 스크립트 개선: 화면 요소 조회가 한 번에 4.7초, 화면 저장이 1.4초 걸려 영상이 늘어지던 것을 확인해 조회·저장을 기본으로 끄고(`-Debug`, 좌표 재측정 `UseLookup`일 때만 사용) 창이 커진 뒤 측정한 고정 좌표를 사용. 전체 소요 207초 → 141초(screenrecord 175초 제한 이내). 에뮬레이터 내부 녹화는 깜빡임·검은 화면 없이 정상(사용자 확인) | 사용자 요청: 팝업 키우기, 영상 대기 시간 단축 |
| v1.0.3 | 2026-11-12 | 시연 영상 재촬영 준비 중 확인한 사실 기록. (1) TTS 소리가 영상에 들어가지 않는 원인: 에뮬레이터 오디오 정책에 출력 장치가 없음(V-08 갱신). 이전 문서의 "TTS 재생 확인"은 이벤트 도착까지만 확인한 것이고 소리가 났다는 뜻이 아니다. (2) `tools/demo-run.ps1` 개선: 주행 1(난폭, 급가속·급제동 각 2회, 점수 76, 보너스 없음)과 주행 2(안전, 구간 A·B, 미션 달성)로 구성, 버튼을 좌표 대신 화면 요소 이름으로 찾아 누름(리포트 창 높이가 내용에 따라 달라 고정 좌표가 빗나가 첫 리포트가 안 닫히던 문제 수정), 단계별 화면 저장, 대기 시간 단축. 이전 재생에서 "문법 검사 통과 후 실행 시 오류"로 스크립트가 시작하자마자 종료된 일이 있어 리허설 필수 | 사용자가 영상을 보고 문제를 지적함 |
| v1.0.2 | 2026-11-12 | 시연 영상 촬영용 자동 재생 스크립트 `tools/demo-run.ps1` 추가(출석 → 주행 → 스팟 보너스 → 급제동 → 리포트 → 포인트 교환, 약 3분 20초). 에뮬레이터 안 `screenrecord`는 소리가 없고 175초 제한이라 보험용으로만 두고, 소리가 들어가는 영상은 윈도우 게임 바(Win+Alt+R)로 에뮬레이터 창을 녹화한다. 촬영 1회 완료: 앱 로그상 출석 +50, 스팟 보너스 2회, 일일 미션 +100, 급제동 감지, 리포트 175P, 교환까지 정상, ANR·크래시 0건. 스팟 **구간** 진입 보너스는 직전 재생에서 위치가 구간 안에 남아 있어 발생하지 않았으며(앱 오류 아님), 재생 시작 시 위치를 구간 밖으로 되돌리도록 스크립트를 고침. 영상 속 TTS 음성 포함 여부는 사용자가 확인 | 시연 영상 촬영(선택 제출, 가산점) |
| v1.0.1 | 2026-11-10 | **중요 버그 수정(V-16).** 앱 화면이 내비게이션 등에 가려지면 시스템이 약 10초 뒤 프로세스를 얼려 주행 기록이 멈추는 문제를 찾아 포그라운드 서비스로 해결(가린 채 2분 유지, 리포트 정상). v1.0에 적은 "앱을 벗어나도 기록이 이어진다"는 관찰 시간이 짧아 틀린 결론이었으므로 정정. 매니페스트에 `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` 권한과 서비스 선언 추가. 심사 허용 여부는 미확인 | 내비를 켜고 운전하는 것이 앱의 핵심 사용 장면인데 그동안 적립이 멈추는 문제였음 |
| v1.0 | 2026-11-10 | **첫 시연 가능한 빌드.** 최종 점검에서 찾은 문제 수정: (1) 자정이 지나도 앱이 켜져 있으면 "오늘 출석 완료"와 오늘 주행거리가 전날 값으로 남던 문제를 날짜 변경 감지로 수정(실행 시험은 못 함, 아래 참고), (2) 앱 아이콘이 기본 안드로이드 로봇이고 이름이 영문이던 것을 초록 코인 "D" 아이콘과 "드라이브캐시"로 변경, (3) 앱 버전 표기 0.1.0 → 1.0.0. 홈 키·뒤로 키로 앱을 벗어나도 주행 기록이 이어지는 것을 확인(거리 0.16km → 0.79km). 릴리스 APK 재빌드 | 개발 완료 후 사용자 요청("더 개발하거나 점검할 것 없나")에 따른 최종 점검 |
| v0.7.6 | 2026-11-10 | 화면 방향 결정(D-029): 가로 레이아웃 유지, 공식 채널에 FAQ #28의 의미를 문의한 뒤 재결정. 9장 항목을 문의 대기로 변경 | 사용자가 에이전트 권고안을 선택 |
| v0.7.5 | 2026-11-10 | 화면 방향 조사 결과 기록: 공식 에뮬레이터 장치 정의가 가로 2560x1440임을 확인, 앱 창 실측, FAQ #28 해석이 확정되지 않음, 세로 흉내 시험이 시스템 UI 때문에 무효였음, 앱에 좁은 폭 분기가 없음. 9장 항목을 결정 대기로 바꿈 | 사용자 요청("조사해줘") |
| v0.7.4 | 2026-11-10 | 시연 녹화 스크립트(`tools/record-demo.ps1`) 시험 결과 기록(파일 생성과 정상 종료만 확인). 기획서 담당자 전달 문서를 `참고자료/`에 작성(개발 완료 후 작성하기로 한 약속 이행). 9장에 화면 방향(세로 기준) 확인 필요 항목 추가 | 개발 완료 후 남은 준비 작업 처리 |
| v0.7.3 | 2026-11-10 | **권한 정정과 릴리스 빌드.** 앱을 삭제 후 새로 설치하면 `CAR_SPEED`와 `FUSED_LOCATION`이 `granted=false`임을 확인했다. 2026-11-09의 "일반 권한으로 자동 허용" 기록은 덮어쓰기 설치 때문에 생긴 오류였으므로 정정하고, 앱이 첫 실행에서 두 권한을 요청하도록 수정했다. 검증 전용 `NAVI_ROUTE_SEARCH`를 매니페스트에서 제거. 저장소 밖 키로 서명하는 릴리스 빌드 설정 추가, 릴리스 APK 신규 설치 검증. DO(FAQ #21)·주행 중 제한 기능(#22)·디자인(#20)·AAOS 정책(#27) FAQ 내용을 4장에 반영. 2차 PT 기준은 여전히 미공지 | 제출용 빌드를 만들면서 새로 설치한 환경에서의 동작을 확인하다가 이전 기록의 오류를 발견 |
| v0.7.2 | 2026-11-10 | TTS 검증 완료(V-08): `speak`의 두 번째 인자가 `isFinal`이며 `true`여야 `onDone`이 온다는 것을 실측하고 `Announcer`를 수정, 안내가 겹쳐 잘리는 문제를 순차 재생 큐로 해결. 바코드 화면 캡처 확인, 주행 시나리오 3회 ANR 0건. 포인트 지급 링크 유지 결정(D-028, D-024 폐기). `tools/verify-all.ps1`의 `-Wait` 대기 문제 수정 | 개발 마무리 단계의 남은 검증 항목 처리 |
| v0.7.1 | 2026-11-09 | 가까운 사용처 카드를 에뮬레이터 화면으로 확인(위치 이동 시 거리·정렬 갱신, 한글 표시 정상). 종류명이 이름에 중복 표시되던 문제("주유소 주유소 (시연 A)")를 장소 이름을 "시연 A/B"로 바꿔 수정. V-06에 경로 요청 성공·경로 안내 중에도 제한속도 0 기록(실차 확인 사항), V-10 갱신, 9장 해당 항목 갱신 | 개발 마무리 단계의 남은 검증 결과 반영 |
| v0.7 | 2026-11-09 | **가까운 사용처 기능 추가(D-027).** 정차 화면에 현재 위치 기준 종류별(주유소·충전소·정비소) 가장 가까운 사용처와 거리를 표시한다. 장소 데이터는 시연용 예시다. `tools/start-emulator.ps1`의 부팅 대기를 `adb` 호출 8초 제한으로 고쳐 무한 대기를 없앴다 | 기획서의 "주변 주유소·충전소·블루핸즈 안내"를 구현하여 기획서 문장을 유지할 수 있게 하기 위해 |
| v0.6.1 | 2026-11-09 | GitHub 제출 방식 결정(D-026): 제출용 새 저장소를 만든다. 9장 해당 항목 갱신 | 제출 규정에 맞는 방법을 사용자가 선택 |
| v0.6 | 2026-11-09 | 9장 남은 과제를 현재 상태 기준으로 재작성(끝난 항목과 중복 제거). 제출 준비 메모를 로컬 전용으로 분리하고 `.gitignore`에 추가. `tools/verify-all.ps1`(남은 검증을 단계별로 실행)과 검증 앱의 내비 경로 요청 기능 추가. 에뮬레이터 종료가 사용자 조작일 수 있어 "오디오 드라이버 때문에 종료" 추정을 철회 | 남은 검증 항목 전체 확인과 공모전 관련 준비 요청 |
| v0.5.5 | 2026-11-10 | 문서 정리. 결정 로그·변경 이력·남은 과제의 표현을 다듬고 10장 참고 링크 추가 | 저장소에 공개하는 문서이므로 제품과 SDK 검증 내용 중심으로 정리 |
| v0.5.4 | 2026-11-10 | 기획서 PDF가 있는 `참고자료/`를 저장소에서 제외(D-025) | 제출용 저장소에 기획서 원본을 포함하지 않기 위해 |
| v0.5.3 | 2026-11-09 | 출석체크·시연용 포인트 지급·포인트 교환·바우처 화면을 에뮬레이터에서 터치로 확인(잔액 계산 일치). 바코드가 대화상자 폭을 넘어 잘리는 문제 수정. 시연용 포인트 지급 링크 추가(D-024). 에뮬레이터가 예고 없이 종료되는 현상이 또 발생해 `start-emulator.ps1`이 출력을 파일로 남기도록 변경 | 남은 화면 확인 |
| v0.5.2 | 2026-11-09 | 급제동 검출을 실측으로 확인(−11.08m/s² → 급제동 1회, 점수 94). 리포트 화면과 로그 수치 일치 확인. ANR은 부팅 후 충분히 기다린 실행에서 0건(가설과 모순 없음, 반복 필요). | 검증 결과 반영 |
| v0.5.1 | 2026-11-09 | 급제동 미검출의 원인(쿨다운 오버플로) 수정과 급가속 검출 실측 반영. ANR 원인 가설 정정: AAOS 호출이 원인이라는 이전 추정을 철회하고 부팅 직후 시스템 과부하 가설로 교체(단일 보고서 근거, 미검증). D-022의 "원인 해결" 표현을 "조치 적용, 효과 미확인"으로 정정. 보조 스크립트 `tools/run-all.ps1` 추가 | 검증 결과가 이전 결론과 달라서 |
| v0.5 | 2026-11-09 | **본 앱 1차 구현.** 주행 엔진(모드 전환·거리·안전점수·포인트), 저장소(포인트·출석·미션·바우처), 신호 계층(Pleos SDK 4종 + AAOS), 정차/주행 화면, TTS 안내를 만들고 에뮬레이터에서 P→D→P 주행 1회를 끝까지 실행해 리포트까지 확인. 발견: 메인 스레드의 AAOS 바인더 호출로 ANR 발생(백그라운드 스레드로 이동), 급제동 판정 미동작 원인 조사 중. 결정 D-020~D-023, 검증 V-14·V-15 추가 | D-006, D-007에 따른 본 개발 |
| v0.4 | 2026-11-09 | **첫 에뮬레이터 실측.** Pleos Connect v2.1.0 AVD 생성과 부팅, SDK 검증 앱 빌드·설치·실행. V-01, V-02, V-03, V-04, V-06, V-07, V-08, V-12, V-13을 실측 결과로 갱신. 차량 속도는 AAOS `PERF_VEHICLE_SPEED`로 가능, 기어 감지 가능, Virtual Console 제어 API 확인. 5장 차이점 갱신, 결정 D-017~D-019 추가 | 기획서의 핵심 기능(주행거리·속도·기어)이 실제로 동작하는지 확인하기 위해 |
| v0.3.1 | 2026-11-09 | SDK 라이선스 동의(D-016). Android SDK 설치 완료. Pleos 시스템 이미지 다운로드와 SHA-1 검증 완료, SDK 경로에 추출 시작. Gradle 프로젝트 골격과 SDK 검증 앱(`SdkVerifier`) 작성. 6장 아키텍처에 프로젝트 구조 추가 | 개발 시작 (D-007) |
| v0.3 | 2026-11-09 | SDK 소스 JAR 분석으로 4장 갱신(V-01~V-09). 차량 속도·가속도가 Pleos SDK에 없음을 확인, 제한속도(NaviHelper)와 기어 API(deprecated)는 존재 확인. 5장에 기획서와의 차이 3건 추가. Android Studio 설치 완료, cmdline-tools 설치, JDK 17 설치 시작. 결정 D-013~D-015 추가 | 개발 시작. 코드를 쓰기 전에 실제 API 이름과 제공 범위를 확정하기 위해 |
| v0.2.1 | 2026-11-09 | D-006, D-007, D-011을 확정으로 변경. 3장 설치 상태에 PC 점검 결과 반영, Android Studio 설치 시작(D-012). 개발 시작 | 개발 시작 |
| v0.2 | 2026-11-09 | 1장에 심사 기준, 진행 단계, 시상 내용 추가. 결정 D-010, D-011, 남은 과제 추가. 참고 링크에 공모전 개요와 심사·시상 페이지 추가 | 공모전 개요와 심사·시상 페이지 확인. 평가 항목과 단계별 요구 사항이 구현 우선순위에 영향을 줌 |
| v0.1.1 | 2026-11-09 | 참고 링크 목록 추가. 결정 D-009 추가 | 매번 출처를 다시 찾지 않고 같은 자료를 기준으로 삼기 위해 |
| v0.1 | 2026-11-09 | 문서 최초 작성. 기획서 요약, SDK 검증 현황, 결정 로그, 아키텍처 제안, 테스트 방법 기록 | 조사 결과와 결정을 잃어버리지 않기 위해 코드보다 먼저 문서화 |

## 1. 목표와 범위

- **목표:** 현대자동차그룹 차량용 앱 개발 공모전에 제출할 "현대 드라이브캐시" 시연용 MVP를 만든다.
- **앱 개념:** 주행거리와 안전운전 점수에 따라 포인트를 적립하고, 현대차그룹 정비·유지비 생태계에서 사용하게 하는 차량 전용 리워드 앱.
- **대상 플랫폼:** Pleos Connect (Android Automotive OS 기반).
- **참고자료:** `참고자료/` 폴더의 기획서 PDF 2종.

### 공모전 일정과 제출 조건

- 접수 기간: 2026-10-21 ~ **2026-11-15 23:59**
- 접수 방법: `appdevchallenge@hyundai-partners.com` 으로 이메일 제출. 메일 제목은 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`.
- 필수 제출물: 접수양식(기획서). 이미 작성되어 있다.
- 선택 제출물 (가산점): 시연 영상, GitHub 저장소, APK.
- 숏폼 링크는 선택이며 가산점이 없다. 본상과 별도로 운영되는 특별상(50만원) 대상이다.
- 공유 링크와 첨부자료에 개인정보, 계정정보, 개발자 실명이 노출되지 않아야 한다. **GitHub 저장소를 제출할 경우 커밋 작성자, 저장소 소유자, README에 실명이 들어가지 않는지 확인해야 한다.**
- 마감 전 수정본은 같은 발신자 이메일로 다시 보내며, 마감 전 마지막 발송본으로 심사한다.

### 참가 자격

- 대상: 대학(원)생, 일반인, 스타트업. 개인 또는 팀(대표자 포함 최대 5인).
- 시상 부문: 대학생 / 일반(스타트업 포함).
- 주제는 자유이며 공모전이 예시로 든 주제(모빌리티 & 라이프, 인카 엔터테인먼트, 스마트 드라이빙 & 안전, 패밀리 & 동승자 케어)에 한정되지 않는다. 기획서의 참가 분야는 "모빌리티 & 라이프 / 엔터테인먼트".

출처: [공모전 개요](https://appdevchallenge.auto-hmg.io/guide/overview)

### 진행 단계

| 단계 | 내용 | 개발 산출물 |
|---|---|---|
| 1차 서류심사 | 접수양식(기획서) 심사 후 2차 대상팀 선정 | 선택 제출, 가산점(단계별) |
| 2차 PT 심사 (오프라인) | 핵심 기능을 구현해 발표. **2차에서 제출·시연한 결과물로 수상작과 순위 확정** | 1차 합격팀은 2차 전까지 실제 동작하는 앱 개발 완료 필수. 1차 제출물은 2차 전까지 자유롭게 고도화 가능 |
| 최종 선정 10팀 | 2027.02 현대자동차그룹 행사에서 전시·시연, 시상식 | 전시 구동용 APK 필수. 이 기간은 추가 개발이 아니라 영상 제작과 구동 테스트 기간 |

- 2차 PT 심사 기준, 수상팀 준수사항(수정·보완 범위, 추가 제출물과 기한)은 추후 공지 예정이다.
- 수상팀 지원: Pleos App Market 탑재 준비 지원. 별도 검증·심사와 지원 차종·지역 조건이 있으며 입점이나 배포를 보장하지 않는다.

### 1차 심사 기준

네 항목 모두 필수 평가이고 세부 배점은 비공개다. 출처: [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards)

| 항목 | 평가 내용 | 이 프로젝트에서의 의미 |
|---|---|---|
| 차량특화성 | 모바일 대비 차량 환경만의 가치. 주행·정차 맥락과 차량 기능의 유기적 결합 | 주행/정차 상태 분리 UI, 주행거리·주행 상태 사용 |
| 창의성 | 독창적인 아이디어. 운전자·동승자의 Pain Point와 해결 방식이 명확한가 | 기획서 2장 기획 배경 |
| 기술 활용성 | Pleos SDK·API를 핵심 기능과 사용 경험에 적절히 활용했는가 | **SDK를 실제로 쓰는 구현이 필요하다. 시뮬레이션만으로는 부족할 수 있고, 기획서에 적은 SDK 활용 계획(내비 안전점수 등)과 실제가 어긋나면 불리할 수 있다** |
| 사업성 | 타깃 고객, 사용자 확보·유지·확대 가능성 (MAU, Retention, 확대 계획) | 기획서 6장 |

가산점: 프로토타입 시연 영상(화면 녹화 포함), GitHub 저장소 링크(소스 코드와 APK 포함). 단계별로 부여한다.

### 시상

총상금 5,100만원. 상금은 본상 기준이며 숏폼 챌린지 어워드는 별도다.

| 상격 | 상금 | 팀 수 |
|---|---|---|
| 대상 | 1,000만원 | 대학생 1팀, 일반 1팀 |
| 최우수상 | 600만원 | 대학생 1팀, 일반 1팀 |
| 우수상 | 400만원 | 대학생 2팀, 일반 2팀 |
| 아이디어 특별상 | 150만원 | 부문 통합 2팀 |
| 숏폼 챌린지 어워드 | 50만원 | 1팀 (별도) |

## 2. 기획서 요약

### 핵심 기능

1. **실시간 미터기 리워드:** 택시 미터기 방식 UI로 주행거리(km)에 비례해 포인트를 실시간 적립.
2. **안전운전 점수와 스팟 보너스:** 안전운전 점수 90점 이상, 또는 급가속·급제동 없는 구간 통과 시 보너스.
3. **데일리 출석체크와 미션:** 연속 출석(Streak) 보너스, 일일 목표 주행거리 미션.
4. **Safety-First UI:** 주행 중에는 미니 위젯(거리, 포인트)만 표시하고, 정차(P단) 시에만 상세 리포트와 미션 화면 표시.
5. **리워드 사용처:** 마이현대/현대샵 교환권, 주유·충전권, 모두의주차장 할인권, 블루핸즈 정비비 직접 차감.

### 사용 시나리오

| 단계 | 상태 | 동작 |
|---|---|---|
| 1 | 주행 전 (P단) | 앱 실행, 출석체크, 오늘의 미션 확인 |
| 2 | 주행 중 (Drive Mode) | 미니 위젯 표시, 거리에 따라 포인트 적립, 안전운전 시 TTS와 함께 스팟 보너스 |
| 3 | 주행 완료 (Park Mode) | P단 전환 시 리포트 팝업 (총 거리, 평균 속도, 안전점수, 획득 포인트) |
| 4 | 리워드 사용 | 쿠폰 교환, 블루핸즈에서 바코드로 정비비 차감 |

### 기획서의 SDK 활용 계획

| SDK | 기획서상 용도 |
|---|---|
| Vehicle SDK | 속도, 급가속/급제동 이력, 횡가속도, 주행거리(Odometer) 수집, 안전운전 점수 산출 |
| NaviHelper SDK | 주행 경로, 제한속도, 내비 연동 안전운전 데이터 파싱 |
| FusedLocation SDK | 위치 추적, 스팟 보너스 구간 진입 판단, 주변 주유소·충전소·블루핸즈 연동 |
| TextToSpeech SDK | 목표 달성과 보너스 음성 안내 |

## 3. 개발 환경

| 항목 | 값 | 출처 |
|---|---|---|
| minSdk | Android API 28 이상 (차량 사이버 보안 요건) | [설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) |
| Gradle | 8.0 이상 | 위와 동일 |
| 언어 | Kotlin 또는 Java | 위와 동일 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, API 34 | 위와 동일 |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` | [샘플 앱](https://github.com/Pleos-Playground/pleos-sample-vehicle-app) |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.5` | [샘플 앱](https://github.com/Pleos-Playground/pleos-sample-navi-app) |
| FusedLocation SDK | `ai.pleos.playground:FusedLocation:1.1.3` | [샘플 앱](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app) |
| TextToSpeech SDK | 버전 미확인 | |
| Maven 저장소 | `https://nexus-playground.pleos.ai/repository/maven-releases/` | 샘플 앱 |
| JDK | 17 (Android Studio 포함 JBR 사용 가능) | Vehicle 샘플 앱 |

### 에뮬레이터 설치 절차 (요약)

1. Android Studio 최신 버전 설치.
2. SDK Manager > SDK Update Sites 에 사이트 추가.
   - 이름: `Pleos Connect System Image`
   - Windows/Linux URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
3. SDK Platforms 탭에서 API 34의 Pleos Connect system image 설치 후 Android Studio 재시작.
4. Device Manager에서 Automotive > Pleos Connect v2.1.0 선택, API 34 이미지.
5. Advanced Settings: Default boot를 Cold, RAM 4GB.

### 설치 상태

| 항목 | 상태 |
|---|---|
| Android Studio | 설치됨 (2026.2.2.8, `C:\Program Files\Android\Android Studio`). 최초 실행과 설정 마법사는 아직 하지 않음 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` 에 cmdline-tools, platform-tools, platforms;android-35, build-tools;35.0.0, emulator 설치됨 (D-016). 새 `sdkmanager`는 deprecated이며 `android sdk install` 로 위임된다. 출력을 리디렉션하면 라이선스 입력 대기에서 멈추므로 `-RedirectStandardInput`으로 `y`를 넘겨야 한다 |
| Pleos 시스템 이미지 | 설치됨. 새 CLI는 사용자 정의 업데이트 사이트 옵션이 없어 이미지 XML이 가리키는 ZIP(4,087,751,964 bytes)을 직접 받았고 XML의 SHA-1(`b4f858d5...`)과 일치함을 확인했다. ZIP 안의 `crp_avd_out_x86_64/*`(`system.img` 약 18GB 포함)를 `Sdk\system-images\android-34\android-automotive\x86_64`에 추출했다. XML의 패키지 경로는 `pleos_car_emulator`이지만 `avdmanager`가 태그(`android-automotive`) 기준 경로를 요구해서 폴더 이름을 `android-automotive`로 바꿨다 |
| AVD | `Pleos_Connect_v2_1` 생성됨(`avdmanager create avd`). `config.ini`를 수정: 2560x1440, 160dpi, RAM 4096, GPU host, landscape, Cold boot, 장치 프레임 없음. `source.properties`에는 RAM 8192가 적혀 있으나 가이드(4GB)를 따랐다. 에뮬레이터 37.2.13, 이미지 빌드 `release_connect_v2.1.0`, Android 14. 이미지에 Pleos 서비스(`vehicle`, `navi`, `location.fused`, `caas`, `connectwebconsole`, `hardware.vehicle.emulator`)가 포함되어 있다 |
| Gradle | wrapper 8.9. AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01. 빌드는 JDK 17로 실행 |
| JDK | Android Studio 내장 JBR은 JDK 25라 Gradle 8 계열과 맞지 않을 수 있음. Eclipse Temurin 17 설치됨 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`, D-015) |
| Gradle, adb | 미설치 (프로젝트에 wrapper 사용, adb는 platform-tools에 포함) |
| git | 설치됨 (2.54.0) |

PC 사양 점검 (2026-11-09): RAM 31.5GB, C: 여유 약 254GB, 하이퍼바이저 감지됨. 에뮬레이터 요구 사항(RAM 4GB 할당)에 충분하다.

## 4. SDK 검증 현황

상태 값: **문서 확인** (문서, 샘플, SDK 소스 JAR의 KDoc에서 확인), **미확인** (근거를 찾지 못함), **에뮬레이터 검증 완료** (실제 호출로 확인), **불가 판단** (SDK 소스에 제공되지 않는 것으로 보임).
에뮬레이터 검증은 아직 하나도 하지 않았다.

v0.3에서 Maven 저장소(`nexus-playground.pleos.ai`)의 SDK 소스 JAR을 직접 받아 API 목록을 분석했다. 샘플 앱의 카탈로그보다 정확하다. 분석 대상: Vehicle 2.3.2, NaviHelper 3.0.5, navi-data 3.0.4, FusedLocation 1.1.3. TextToSpeech 3.3.1은 소스 JAR이 없어 아직 분석하지 못했다. 저장소는 인증 없이 접근된다.

| ID | 항목 | 상태 | 비고 | 대체안 |
|---|---|---|---|---|
| V-01 | 주행거리 (Odometer) | 에뮬레이터 검증 완료 | `Vehicle.getOdometer()`의 `checkCurrentDistanceDrivenCapabilities`는 `accessType=READ`. `getCurrentDistanceDriven`은 Float(km)를 반환하고, Virtual Console로 100 → 100.5 → 101을 주입하자 `registerDistanceDriven` 리스너와 get이 같은 값을 반환했다. 리스너는 값이 같아도 약 3~4초마다 호출된다. **에뮬레이터는 속도로 주행거리를 적분하지 않는다**(속도 60km/h 동안에도 100.0 유지). 퍼미션 `pleos.car.permission.CAR_DRIVE` (normal) | 실차에서는 계기판 값을 쓰고, 에뮬레이터에서는 Virtual Console로 주입 |
| V-02 | 차량 속도 | 에뮬레이터 검증 완료 (AAOS 표준 속성) | Pleos Vehicle SDK 2.3.2와 FusedLocation에는 속도 API가 없다. 대신 `android.car.Car` → `CarPropertyManager.getFloatProperty(0x11600207 /*PERF_VEHICLE_SPEED*/, 0)`가 동작한다. 리플렉션으로 호출했고 매니페스트에 `uses-library android.car (required=false)`와 `android.car.permission.CAR_SPEED`를 선언했다. Virtual Console에서 60km/h → 16.67m/s, 20km/h → 5.56m/s, 0 → 0m/s로 반영됐다(1초 폴링). 읽기 가능한 AAOS 속성은 9개: `0x11200402, 0x11200403, 0x11400400, 0x11400401, 0x1140040c, 0x1140040d, 0x11510306, 0x11600207, 0x11600208`. 설치는 `adb install -g`(런타임 권한 일괄 허용)로 했다. 미확인: `-g` 없이 설치했을 때 CAR_SPEED 허용 여부, Pleos App Market 심사에서 `android.car.permission.*` 선언이 허용되는지 | 위치 기반 환산은 불가(FusedLocation에 속도 없음, 에뮬레이터 위치도 고정). 실차에서 AAOS 속도가 막히면 주행거리 변화율 사용 |
| V-03 | 급가속/급제동, 횡가속도 | 불가 판단 (Pleos SDK 한정), 대체 구현 가능 | 가속도·횡가속도 API 없음. 브레이크 페달 on/off(`registerBrakePedalStatus`)는 에뮬레이터에서 동작한다(브레이크 80~100 입력 시 `true`). 속도(V-02)를 폴링해 시간 변화율로 가속도를 계산할 수 있다. 단, 에뮬레이터 속도는 PUT 즉시 계단식으로 바뀌어 실제 가속 곡선이 나오지 않는다 | 속도 변화율 + 브레이크 페달 보조 신호. 임계값은 실차 데이터 없이 정해야 하므로 설정값으로 둔다 |
| V-04 | 기어(P단) 감지 | 에뮬레이터 검증 완료 (deprecated API) | `Vehicle.getGear()`: `getCurrentGearSelectionState` → `GEAR_PARK`, `registerGearSelection` 리스너가 Virtual Console의 Park → Drive → Park 변경을 실시간 전달했다. AAOS `GEAR_SELECTION`(0x11400400)도 읽힌다(값 4 = Park). Pleos `Gear` API는 `@Deprecated("use the Google GEAR_SELECTION property")`, `@hide`, 샘플 카탈로그 제외 상태다. 퍼미션 `CAR_DRIVE`. 미확인: 앱 심사에서 deprecated/hidden API 사용 허용 여부 | AAOS `GEAR_SELECTION`을 주 소스로, Pleos Gear API를 보조로 사용. 둘 다 안 되면 "정차 N초" |
| V-05 | 내비 안전운전 점수 | 불가 판단 | `NaviHelperEventListener`의 전체 콜백(`onRouteStarted, onRouteEnded, onRoutingCompleted, onReRouteEnded, onRouteOptionChanged, onRouteCancelled, onWaypointChanged, onDestinationArrived, onWayPointArrived, onBookMarkChanged, onDestinationChanged, onError, onCurrentLocationInfo, onRouteStateInfo, onDestinationInfo, onWayPointInfo, onRecentDestinationInfo, onBookmarkInfo, onCurrentRoadSpeedLimit, onCameraAlert, onDrivingInfo, onNavigationStatus, onTBTInfo, onChargerOperatorInfo, onCautionPointInfo, onTripInfo`)에 안전운전 점수가 없다. `DrivingInfo`는 목적지와 경유지의 이름, 소요 시간, 거리, 충전 정보다 | 자체 안전운전 점수 모델 |
| V-06 | 현재 도로 제한속도 | 에뮬레이터 검증 완료 (부분) | `onCurrentRoadSpeedLimit(speedLimit: Int)`가 리스너 등록 후 매초 호출된다. 경로 안내가 없는 상태에서는 항상 `0`이었다(70초 관찰, 값 종류 `[0]`). `NaviHelper.initialize(onConnected, onFailed)` 연결 성공, 조회용 퍼미션 `NAVI_ROUTE`. **경로 요청(`requestRoute`)에는 `pleos.car.permission.NAVI_ROUTE_SEARCH`가 추가로 필요**하다(`NAVI_ROUTE`만 있으면 `SecurityException`, 2026-11-09 실측). `NAVI_ROUTE_SEARCH`를 더해 경로를 요청하자 안내가 시작됐다(`ROUTE_DRIVE`, 13000m, 3360초, 통행료 700). **그런데 경로 안내 중에도 `onCurrentRoadSpeedLimit`은 `0`이었다**(에뮬레이터 내비 위치가 V-13처럼 고정이라 도로를 따라가지 않는 것으로 추정, 미확인). 따라서 제한속도 값과 단위(km/h 추정)는 에뮬레이터에서 확인할 수 없고 실차 확인 사항이다. `NAVI_ROUTE_SEARCH`는 Pleos App Market의 사전 승인 대상일 수 있어, 앱 본체가 경로 요청을 하지 않는 한 선언하지 않는다 | `0`이면 제한속도 정보 없음으로 처리 |
| V-07 | 위치 갱신, 근접 알림(ENTER/EXIT) | 에뮬레이터 검증 완료 (위치 갱신까지) | `FusedLocation.isSupported()=true`, 런타임 퍼미션 `pleos.car.permission.FUSED_LOCATION` 필요. `getLastKnownLocation`과 `registerFusedLocationCallback`이 1초마다 `LocationData`를 전달했다. 기본 위치는 `37.421998, -122.084`(에뮬레이터 기본값), `heading=0.0`, `confidence=5.0`, 고도 5.0. 근접 알림(`addProximityAlert`)과 `adb emu geo fix` 이동은 아직 시험하지 않았다 | |
| V-08 | TTS | 에뮬레이터 검증 완료 | `TextToSpeech(context, Mode.ON_DEVICE).initialize(listener)` 성공. 퍼미션 `pleos.car.permission.TTS_SERVICE` 필요(없으면 `SecurityException`). `addEventListener`는 `initialize` 성공 콜백 안에서 호출해야 한다(전에 호출하면 `UninitializedPropertyAccessException`). 에뮬레이터를 오디오 켠 상태(`-Audio`)로 띄워 시험했다. **`speak(text, isFinal)`의 두 번째 인자는 `isFinal`이다**(SDK 로그 `doSpeak(...) isFinal=`). `false`이면 `onReady`, `onStart`까지만 오고 `onDone`이 오지 않았다(36초 관찰). `true`이면 `onReady → onStart → onDone`이 약 2.5초 안에 모두 왔다. 본 앱 주행 시나리오에서 안내 3건(주행 시작, 스팟 구간, 안전운전 보너스)이 순서대로 시작·완료됐다. 안내가 겹치면 앞 안내가 잘려서 `Announcer`에 순차 재생 큐를 넣었다(완료 이벤트가 12초 안에 안 오면 다음으로 진행). **2026-11-12 사용자가 소리를 켜고 영상을 찍었으나 음성이 들리지 않았다.** 원인 조사: 안드로이드 볼륨은 정상(CarAudioService 볼륨 그룹 음소거 아님, 20/45)이지만, 오디오 정책 로그에 `getDevicesForStrategyInt no device found for strategy 0~9`가 모든 용도에서 반복된다. 즉 이 에뮬레이터 이미지의 오디오 정책에는 **출력 장치가 하나도 없어** 앱이 낸 음성이 PC 스피커까지 나가지 않는 것으로 보인다(`AudioTrack ... deviceInfo=null`과도 일치). 에뮬레이터를 `-Audio`로 띄워도 같았다. 따라서 **이 환경에서는 TTS 소리를 들을 수도, 영상에 담을 수도 없다.** 재생 이벤트(`onStart`/`onDone`)가 정상인 것은 음성 엔진이 합성을 마쳤다는 뜻일 뿐 소리가 났다는 증거가 아니다. 실제 소리는 실차 또는 오디오 출력이 있는 환경에서만 확인할 수 있다 | 실차 또는 소리를 들을 수 있는 환경에서 청취 확인 |
| V-09 | 내비 옆 미니 위젯 | 미확인 | 다른 앱 화면에 위젯을 얹는 API를 찾지 못함. NaviHelper 소스에 `InternalApi.updateOccupiedRegion(region: Rect?)`와 `OccupiedRegionRequest` 모델이 있어 내비에 "앱이 차지하는 화면 영역"을 알리는 용도로 보이나, 내부 API인지와 용도는 미확인 | 앱 자체 화면의 주행 모드 |
| V-10 | 주변 주유소·블루핸즈 검색 | 미확인 | 실제 POI 검색 API는 찾지 못했다(FusedLocation은 위치만 제공). 대신 시연용 고정 목록으로 "가까운 사용처" 카드를 구현했다(D-027). 이 카드는 에뮬레이터에서 동작을 확인했다: `geo fix`로 위치를 옮기자 주유소 377m→370m, 정비소 691m→1.4km(정렬 순서 변경)로 갱신됐고 한글 표시가 정상이다. 목록은 가짜 좌표의 예시이며 실제 영업점이 아니다 | 고정 목록(구현 완료, 화면에 "시연용 예시" 표기) |
| V-11 | 마이현대/현대샵/블루핸즈 연동 | 불가 판단 | 공개 API 없음 | 목업 |
| V-12 | 에뮬레이터 Virtual Console로 차량 값 주입 | 에뮬레이터 검증 완료 | 에뮬레이터 안에서 포트 8088의 웹 앱(`ai.pleos.connectwebconsole`)이 동작한다. `adb forward tcp:8088 tcp:8088` 후 `http://localhost:8088`. **브라우저 하트비트 `POST /browser/session?client=<id>`를 1초마다 보내야 활성화**되며, 없으면 모든 PUT이 `503 emulator_inactive`이다(상태의 `browser.active`로 확인). 확인된 호출: `PUT /vehicle/gear?gear=Park/Reverse/Neutral/Drive`, `PUT /vehicle/controls?speed=<0~240kph>&accelerator=<0~100>&brake=<0~100>&steering=<-540~540>`, `PUT /vehicle/odometer?traveled_distance_km=<0~1000000>`, `GET /vehicle/state`, `GET /vehicle/catalog`. 이 엔드포인트는 공식 문서가 아니라 콘솔 페이지의 JS와 카탈로그에서 알아냈다. `adb shell cmd car_service inject-vhal-event`는 user 빌드라 막혀 있다 | 시연과 자동 테스트에 사용 |
| V-14 | 주행 중 앱 화면 유지(Distraction Optimized) | 에뮬레이터 검증 부분 완료 | 런처 로그에 앱 등록 시 `distractionOptimized: false`가 찍혔다. 매니페스트 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 추가했다. 에뮬레이터는 Virtual Console로 속도 60km/h·기어 D인 상태에서 앱 화면이 계속 표시됐다(캡처 확인). 실차의 주행 중 차단 정책, 이 선언을 Pleos가 인정하는지는 미확인. FAQ "DO란 무엇인가요"는 내용 미열람 | 심사 가이드와 FAQ 확인 |
| V-16 | 앱 화면이 가려진 동안 주행 기록 유지 | 에뮬레이터 검증 완료 | 위 9장 항목 참조. 서비스 없이는 가린 뒤 약 10초 만에 프로세스가 얼어 기록이 멈춘다. 포그라운드 서비스(타입 `specialUse`, 권한 `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`)로 해결했다. 서비스 알림은 `IMPORTANCE_MIN`이라 눈에 띄지 않는다. 시험 중 에뮬레이터의 내비 앱이 CPU 과다 사용으로 시스템에 종료되고 런처가 내비를 자동 재실행하며 우리 앱을 가리는 일이 있었는데, 같은 계열의 시스템 과부하로 보인다. **미확인: Pleos App Market 심사에서 포그라운드 서비스와 `specialUse` 사용이 허용되는지** | 허용되지 않으면 서비스를 빼고, 대신 "앱이 화면에 보이는 동안만 기록"으로 범위를 줄여야 한다 |
| V-15 | 근접 알림으로 스팟 구간 진입 감지 | 에뮬레이터 검증 완료 | `addProximityAlert`(ENTER)로 구간 2개를 등록하면 `onRegistered(alertId)`가 오고, `adb emu geo fix -122.0800 37.4300`으로 위치를 구간 A 안으로 옮기자 `onTransition(ENTER)`가 발생해 구간 보너스가 지급됐다. 앱을 `pm clear` 한 뒤에는 런타임 퍼미션 `FUSED_LOCATION`을 다시 부여해야 등록된다(앞선 `구간=0/2`의 원인). 이로써 V-07의 `geo fix` 위치 이동 시험도 완료. 단, 내비게이션(V-13)의 위치는 별개 소스라 따라 움직이는지는 미시험 | |
| V-13 | 에뮬레이터에서 내비 위치 갱신 | 에뮬레이터 검증 완료 (조회만) | `getCurrentLocationInfo` 결과는 `37.4127212, 127.094681`(FusedLocation 기본 위치와 다름). **`adb emu geo fix`를 세 번 바꿔도(서울 강남, 북동 300m, 더 먼 위치) 내비 위치는 변하지 않았다**(70초 관찰). 같은 시간에 FusedLocation 위치는 `geo fix`를 정확히 따라갔다. 내비게이션이 에뮬레이터의 `geo fix`와 별개의 위치 소스를 쓰는 것으로 보인다. 한 사용자의 같은 보고가 있었다. [Discussion #29](https://github.com/Pleos-Playground/pleos-playground-api/discussions/29) | 내비 연동 기능(경로 안내 중 제한속도 등)은 에뮬레이터에서 값을 바꿔 가며 시험하기 어렵다. 앱 위치 기능은 FusedLocation만 사용한다 |

### 제약 조건 (문서 확인)

- **퍼미션:** Pleos App Market과 사전 협의·승인된 범위만 사용할 수 있다. 승인되지 않은 퍼미션을 선언하거나 해당 API를 호출하면 앱 심사를 통과할 수 없다. [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- **Vehicle SDK 지원 여부 확인:** 도메인별 `checkXXXCapabilities` API를 사용한다.
- **화면 방향:** FAQ #28("앱을 Landscape로 실행할 수 있나요?")의 답은 차종에 따라 기준이 다르고, Infolink는 앱이 단독 실행이라 무관하며, Connect S/L/W는 Portrait로 실행되는 것이 기준이고 Landscape는 허용되지 않는다는 내용이다. [FAQ #28](https://github.com/Pleos-Playground/pleos-playground-api/discussions/28) 이 답에서 "Portrait"가 기기 화면 방향인지, 앱이 방향을 고정하지 말라는 뜻인지, 앱 영역의 모양인지는 **답변만으로 알 수 없다.** 2026-11-10 조사 결과는 아래와 같다.
  - **공식 에뮬레이터 장치 정의**(`system-images\...\devices.xml`)는 `Pleos Connect v2.1.0`, 12.8인치, **2560x1440, 기본 상태 Landscape**다. 공모전 개발 환경 안내와 에뮬레이터 설정 문서([링크](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator))는 이 장치로 AVD를 만들라고 안내한다. 따라서 심사자가 쓰는 에뮬레이터도 가로 화면일 가능성이 높다(추정, 공식 확인 아님)
  - **실측:** 이 에뮬레이터에서 앱 창은 약 1682x1210dp이고 좌표는 (853,90)~(2535,1300)이다. 왼쪽 853px은 시스템 앱 Driving view가 차지하고 앱은 오른쪽 영역에 뜬다. 앱은 방향을 지정하지 않는다(`SCREEN_ORIENTATION_UNSPECIFIED`, `areBoundsLetterboxed=false`)
  - **세로 시험 실패:** `wm size 1440x2560`으로 세로를 흉내 냈으나 시스템 UI(Driving view, 하단 바)가 2560x1440 고정 배치라 시스템이 먼저 깨져서 결과를 신뢰할 수 없다. 세로 차종에서의 동작은 **확인하지 못했다**
  - **앱 레이아웃:** 정차 화면은 3열 고정 `Row`이고 밀도를 앱 창 폭 비례로 올려(폭 1100dp 기준, 1~2배) 그린다. 폭이 좁은 환경을 위한 분기는 없다. 폭이 약 1100dp 미만이거나 세로로 길면 3열이 좁아지고 글자가 잘릴 수 있다(코드 확인, 실행 확인은 못 함)
- **일반 안드로이드 폰:** Pleos Vehicle Service가 없어 차량 속성을 호출할 수 없다.
- **DO(Distraction Optimized):** 차량이 움직이면 대부분의 일반 앱 액티비티는 UX 제한 상태에서 자동 차단된다. DO로 태그된 액티비티만 주행 중에도 실행된다. [FAQ #21](https://github.com/Pleos-Playground/pleos-playground-api/discussions/21)
- **주행 중 제한 기능:** 동영상·게임·사진 보기, 텍스트 입력(키보드가 필요한 화면), 웹 브라우저는 주행 중 사용할 수 없다. 음성 입력(ASR)과 읽어주기(TTS)는 허용된다. [FAQ #22](https://github.com/Pleos-Playground/pleos-playground-api/discussions/22) 이 앱의 주행 화면은 조작 버튼과 텍스트 입력이 없고 음성 안내만 쓰므로 이 범위에 맞다(D-023). DO 인정 여부는 App Market 심사에서 정해져 에뮬레이터로는 확인할 수 없다
- **디자인:** Pleos 디자인 가이드와 Google Automotive OS 디자인 가이드를 따르라고 안내한다. [FAQ #20](https://github.com/Pleos-Playground/pleos-playground-api/discussions/20)
- **AAOS 호환 정책:** Pleos SDK는 같은 기능이 Android Automotive OS에 있으면 AAOS에 맞추는 것을 정책으로 한다. 이 앱이 속도·기어를 AAOS 표준 속성으로 읽는 방식(D-017, D-018)과 방향이 같다. 다만 `android.car.permission.*` 선언이 심사에서 허용되는지는 여전히 미확인이다. [FAQ #27](https://github.com/Pleos-Playground/pleos-playground-api/discussions/27)
- **기본 지도 앱:** 현재 우선순위 설정은 지원되지 않고 시스템이 자동 선택한다. [FAQ #24](https://github.com/Pleos-Playground/pleos-playground-api/discussions/24)

## 5. 기획서와 달라지는 점

| 기획서 | 구현 | 이유 |
|---|---|---|
| Vehicle SDK로 실시간 차량 속도, 급가속/급제동 이력, 횡가속도 수집 | Vehicle SDK에는 해당 API가 없다. 속도는 AAOS 표준 속성 또는 위치·주행거리 변화량으로 구하고, 급가속/급제동은 속도 변화율로 계산 | V-02, V-03 |
| 내비 안전운전 점수 90점 | 속도 변화 기반 자체 점수 | V-05 |
| NaviHelper로 현재 도로 제한속도 파싱 | 그대로 가능. `onCurrentRoadSpeedLimit` 사용. 경로 안내 중이 아닐 때의 수신 여부는 미확인 | V-06 |
| 내비 화면 측면 미니 위젯 | 앱 자체 화면의 주행 모드 | V-09 |
| P단 전환 시 리포트 | 정차 N초 감지(기어 확인 시 P단 우선) | V-04 |
| 횡가속도 활용 | 보류 | V-03 |
| P단 전환 시 리포트 (v0.4 갱신) | 기어 신호로 P단 감지 가능(AAOS `GEAR_SELECTION` 또는 Pleos Gear API). "정차 N초"는 보조 수단 | V-04 |
| 주변 주유소·블루핸즈 연동 | 고정 목록 | V-10 |
| 마이현대·블루핸즈 연동 | 목업 데이터. 제휴 필요 사항으로 명시 | V-11 |

이 표의 내용은 V-항목이 에뮬레이터에서 검증되면 바뀔 수 있다.

주의: 위 차이점은 이미 작성된 기획서와 다른 부분이다. 1차 심사의 "기술 활용성"은 기획서에 적은 SDK 활용 계획을 기준으로 평가될 수 있으므로, 어긋나는 항목은 마감 전에 기획서를 수정할지 결정해야 한다. (D-011)

## 6. 아키텍처 (제안)

상태: 제안. 확정 전이며 결정 로그 D-006 참조.

- Kotlin, Jetpack Compose, 단일 모듈 앱.
- 차량 데이터는 인터페이스 뒤에 둔다. 실제 SDK 구현과 시뮬레이션 구현을 모두 만들어, 속도나 기어 API를 쓸 수 없어도 시연이 가능하게 한다.
- 주행/정차 상태 머신으로 주행 모드 화면과 정차 모드 화면(리포트, 출석, 포인트샵)을 분리한다.
- 안전운전 점수는 속도 변화량으로 계산하는 자체 모델.
- 포인트와 출석 기록은 로컬 저장. 리워드 교환과 정비 차감은 목업.
### 프로젝트 구조 (현재)

```
build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew(.bat)   Gradle 설정 (루트가 Android 프로젝트)
app/build.gradle.kts                      minSdk 28, compileSdk/targetSdk 35, Pleos SDK 4종 + Compose
app/src/main/AndroidManifest.xml          퍼미션: CAR_DRIVE, FUSED_LOCATION, NAVI_ROUTE (+ 검증용 android.car.permission.CAR_SPEED, CAR_POWERTRAIN)
app/src/main/java/com/drivecash/app/
  MainActivity.kt                         SDK 검증 화면. --ez autorun true 로 자동 실행
  verify/SdkVerifier.kt                   V-01~V-13 검증. 결과는 화면과 Logcat(태그 DriveCashVerify)에 `[V-xx] 메시지`로 출력
tools/app-scenario.ps1                    본 앱 주행 시나리오(가속·순항·구간 진입·급제동·정차) 재생 + 화면 캡처 + 로그
tools/start-emulator.ps1                  에뮬레이터 시작과 부팅 대기 (-Audio 옵션)
tools/drive-scenario.ps1                  Virtual Console로 주행 시나리오 재생 + 앱 로그 저장
docs/DEVELOPMENT.md                       이 문서
local.properties                          sdk.dir (git 제외)
```

### SDK 사용 규칙

- SDK 호출은 비동기 콜백이며 `initialize()` 완료 후에만 속성 API를 호출한다. 리스너는 `release()` 전에 해제한다. (Vehicle 샘플의 사용 규칙)

## 7. 테스트 방법

- 안드로이드 폰은 필요 없다. 차량 API는 Pleos Connect 에뮬레이터에서만 테스트할 수 있다.
- 위치는 `adb emu geo fix <경도> <위도>` 로 주입한다. 경도가 먼저다. FusedLocation 샘플은 mock location 허용이 필요하다.
  ```
  adb shell appops set --user current <패키지명> android:mock_location allow
  ```
- 차량 값 주입은 Virtual Console(에뮬레이터 안 포트 8088)을 쓴다. 사용법은 V-12 참조.
- 반복 가능한 순서 (모두 저장소 루트에서 실행):
  1. `powershell -ExecutionPolicy Bypass -File tools\start-emulator.ps1` (TTS 시험은 `-Audio` 추가)
  2. 빌드: `gradlew.bat assembleDebug` (환경 변수 `JAVA_HOME`을 JDK 17로 지정)
  3. `powershell -ExecutionPolicy Bypass -File tools\drive-scenario.ps1` → `%TEMP%\drivecash-scenario\events.txt`, `log.txt`
- 로그를 직접 볼 때는 `adb logcat -d -v time -s DriveCashVerify:I`. 콘솔 코드 페이지 때문에 한글이 깨지므로 UTF-8 바이트로 저장해서 읽는다.
- 에뮬레이터 부팅은 약 1분 50초(8코어, 오디오 켬). 부팅 직후 1분은 시스템 초기화로 부하가 높다.
- `adb logcat -s <태그>` 필터는 이 환경에서 빈 결과를 냈다. 전체를 받아 앱 태그 줄만 걸러낸다.
- 시나리오 스크립트에서 `adb shell pm grant`가 예외를 내면(예: `CAR_POWERTRAIN`) PowerShell이 stderr 때문에 스크립트를 멈춘다. `cmd /c ... >nul 2>&1`로 감싼다.
- 에뮬레이터 검증 결과는 4장 표의 상태와 비고에 반영하고, 변경 이력에 한 줄 추가한다.

## 8. 결정 로그

상태 값: **확정**, **제안** (확정 전), **폐기**.

| ID | 날짜 | 결정 | 이유 | 상태 |
|---|---|---|---|---|
| D-001 | 2026-11-09 | 프로젝트는 이 워크스페이스(`drive_cache`)에서 진행한다 | 프로젝트 시작 시 확정 | 확정 |
| D-002 | 2026-11-09 | 코드 전에 문서를 만들고, 결정과 변경을 기록한다 | 검증 결과와 결정 이유를 잃지 않기 위해 | 확정 |
| D-003 | 2026-11-09 | 문서는 `docs/DEVELOPMENT.md` 한 파일에 두고 맨 위 변경 이력 표로 버전을 관리한다. 실제 변경 내역은 git이 담당한다 | 문서 변경을 한 곳에서 추적하기 위해 | 확정 |
| D-005 | 2026-11-09 | 테스트는 PC의 Pleos Connect 에뮬레이터로 한다. 안드로이드 폰은 쓰지 않는다 | 폰에는 Pleos Vehicle Service가 없음 | 확정 |
| D-006 | 2026-11-09 | Kotlin + Compose, 차량 데이터 인터페이스 뒤에 실제/시뮬레이션 구현을 두는 구조로 만든다 | SDK 일부가 미확인이라 시뮬레이션이 있어야 시연이 가능. 단, 시뮬레이션은 보조 수단이며 핵심 기능은 실제 SDK 호출을 우선한다(D-010) | 확정 |
| D-007 | 2026-11-09 | 먼저 SDK 호출 가능 여부를 확인하는 최소 앱을 만들고, 그 결과로 4장 표를 갱신한 뒤 본 기능을 구현한다 | 미확인 항목이 많아 먼저 만들어 보고 확인하는 편이 빠름 | 확정 |
| D-008 | 2026-11-09 | Android Studio와 에뮬레이터 설치는 필요한 시점에 한다 | 필요하기 전에 설치하지 않기 위해 | 확정 |
| D-009 | 2026-11-09 | 공모전 안내와 SDK 문서 링크를 10장에 모아 두고, SDK와 규정 관련 판단은 이 링크를 먼저 확인한다. 링크 내용은 바뀔 수 있으므로 중요한 판단 전에는 다시 확인한다 | 출처를 일관되게 쓰기 위해 | 확정 |
| D-010 | 2026-11-09 | 공모전 개요와 심사·시상 페이지를 10장 참고 링크에 포함한다. 심사 4항목(차량특화성, 창의성, 기술 활용성, 사업성)을 기능 우선순위와 기획서 수정 판단의 기준으로 삼는다 | 평가 기준과 단계별 요구 사항이 구현 범위와 우선순위를 좌우함. 특히 기술 활용성은 SDK를 실제로 쓰는지를 봄 | 확정 |
| D-011 | 2026-11-09 | 1차 마감(11/15) 전 목표를 다음 순서로 둔다: (1) SDK 실호출 검증 (2) 시연 영상 (3) 가능하면 APK와 GitHub. 검증 결과 기획서의 SDK 활용 계획이 실제와 크게 어긋나면 마감 전에 수정본 재제출 여부를 결정한다 | 개발 산출물은 가산점이고, 2차 PT 전까지 실제 동작 앱이 필수라 본 개발은 1차 합격 후에도 가능. 반면 기획서는 마감 전 마지막 발송본으로 심사되므로 이 시점의 정확성이 중요 | 확정 |
| D-012 | 2026-11-09 | Android Studio를 winget(`Google.AndroidStudio`)으로 지금 설치한다. SDK와 Pleos 시스템 이미지는 설치 후 이어서 진행한다 | 개발 시작. 11/15 마감까지 시간이 짧음 | 확정 |
| D-013 | 2026-11-09 | 차량 속도는 다음 순서로 시도한다: (1) AAOS 표준 속성 `PERF_VEHICLE_SPEED` (2) 주행거리(`registerDistanceDriven`) 변화량 / 시간 (3) 위치 변화량 / 시간. 급가속·급제동은 이 속도의 변화율로 계산한다. 속도 소스는 인터페이스 뒤에 두어 에뮬레이터 검증 결과에 따라 교체한다 | Vehicle SDK와 FusedLocation에 속도 API가 없음(V-02). 어느 소스가 에뮬레이터에서 값을 주는지는 아직 모름 | 제안 |
| D-014 | 2026-11-09 | Android SDK와 Pleos 시스템 이미지는 라이선스 내용을 확인하고 동의한 뒤에 설치한다. 이전까지는 라이선스와 무관한 작업(문서, 프로젝트 골격)만 진행한다 | 두 SDK 모두 설치 시 라이선스 동의가 필요하고, Pleos 라이선스에는 향후 사용료 부과 가능, 리버스 엔지니어링 금지, 사용 통계 수집(사전 동의), 서울중앙지방법원 관할 등이 있어 개발자가 직접 판단할 사항 | 확정 |
| D-016 | 2026-11-09 | Android SDK 라이선스와 Pleos SDK 라이선스(2024-01-02 개정판)에 동의하고 설치한다. 주요 조항: 향후 사용료 부과 가능, 리버스 엔지니어링 금지, 사용 통계는 사전 동의, 서울중앙지방법원 관할 등. 필요하면 언제든 SDK를 삭제할 수 있다 | D-014의 라이선스 확인 결과. 동의 범위는 이 프로젝트의 개발 환경 설치에 한정 | 확정 |
| D-017 | 2026-11-09 | 차량 속도는 AAOS `PERF_VEHICLE_SPEED`(리플렉션)를 주 소스로 쓰고(D-013의 (1) 확정), 주행거리 변화율은 보조로 둔다. 위치 환산 속도는 쓰지 않는다 | 에뮬레이터에서 AAOS 속도가 Virtual Console 값을 그대로 따랐고, 위치는 고정이라 환산이 불가능함 | 확정 |
| D-018 | 2026-11-09 | 기어는 AAOS `GEAR_SELECTION`을 주 소스로, Pleos Gear API(deprecated)를 보조로 쓴다. 정차 N초 감지는 둘 다 실패할 때의 대체 수단이다 | 둘 다 에뮬레이터에서 Park/Drive 전환을 정확히 전달했고, Pleos Gear API는 deprecated라 제거될 수 있음 | 확정 |
| D-019 | 2026-11-09 | 에뮬레이터 테스트는 Virtual Console을 HTTP로 호출하는 스크립트로 자동화한다. 앱 검증 로그는 Logcat 태그 `DriveCashVerify`의 `[V-xx]` 형식을 유지한다 | 사람이 콘솔을 조작하지 않고도 같은 시나리오를 반복해 검증할 수 있음 | 확정 |
| D-020 | 2026-11-09 | **포인트·안전운전 기준값은 가정이다.** 거리 10P/km, 0.5km 무사고마다 스팟 +30P, 구간 진입 +20P(구간당 주행 1회), 점수 90 이상이면 거리 포인트의 50% 추가, 출석 50P(+연속일×10P, 최대 7일), 미션 100P(오늘 1km 이상 + 점수 90 이상), 급가속 ≥ 2.8m/s², 급제동 ≤ -3.0m/s², 위반 1회당 6점 감점, 제한속도 110% 초과 시간 비례 감점(최대 40점), 12m/s² 초과 가속도는 신호 점프로 무시. 모두 `domain/RewardPolicy.kt` 한 곳에 둔다 | 기획서에 수치가 없다. 실차 데이터 없이 정한 시작값이므로 제출·발표 시 "가정"임을 밝힌다 | 제안 |
| D-021 | 2026-11-09 | 주행 거리는 속도의 사다리꼴 적분으로 계산하고, 계기판 주행거리(Odometer) 차이는 리포트에 교차 확인 값으로만 기록한다 | 에뮬레이터는 속도로 주행거리를 적분하지 않고(V-01), 실차 계기판의 해상도는 미확인 | 확정 |
| D-022 | 2026-11-09 | AAOS 속성 읽기는 메인 스레드가 아니라 별도 스레드(0.5초 간격)에서 하고, 메인 스레드는 마지막 값만 읽는다 | 첫 주행 시험에서 앱이 `Input dispatching timed out`(ANR)로 종료됨. 바인더 동기 호출이 메인 스레드를 막은 것으로 **추정**했으나, 조치 후에도 ANR이 한 번 더 발생해 원인으로 확인되지 않았다(v0.5.1). 메인 스레드에서 동기 바인더 호출을 빼는 것 자체는 안전한 방향이라 유지한다. **조치 적용, 효과 미확인** | 확정 |
| D-023 | 2026-11-09 | 주행 모드 화면은 조작 요소 없이 거리·포인트·안전점수·속도·제한속도만 보여주고, 출석·교환·리포트 확인은 정차 모드에서만 한다. 개발자 검증 화면은 정차 화면 하단 링크로만 연다 | 기획서의 Safety-First UI. 주행 중 상호작용 금지 | 확정 |
| D-024 | 2026-11-09 | 정차 화면 하단에 "시연용 포인트 +10,000P" 링크를 둔다(정차 중에만 동작). 교환 화면 시험과 시연 영상 촬영에 필요하다. 시험용 기능이므로 제출용 빌드에서 제거할지 제출 전에 정한다 | 포인트가 303P라 최저가 교환 항목(2,000P)도 눌러 볼 수 없었다 | 폐기 (D-028로 대체: 제출 빌드에 유지하기로 정함) |
| D-025 | 2026-11-10 | 기획서 PDF가 있는 `참고자료/`는 git에 올리지 않는다 | 기획서에는 제출 규정상 노출하면 안 되는 정보가 있을 수 있음 | 확정 |
| D-029 | 2026-11-10 | 화면 방향은 **현재 가로 레이아웃을 유지**하고 좁은 폭용 분기는 추가하지 않는다. 대신 FAQ #28의 "Portrait 기준"이 무엇을 뜻하는지 공식 채널에 물어보고, 답에 따라 다시 정한다. 질문 문안은 로컬 제출 메모(`docs/SUBMISSION.md` 7장)에 있다 | 공식 에뮬레이터가 가로 2560x1440이라 현재 앱이 그 환경에서 동작한다(V-14, 4장 조사). "Portrait"의 의미가 불확실하고 세로 환경을 제대로 시험할 수 없어서, 근거가 약한 가정을 위해 코드를 늘리지 않는다. 사용자가 에이전트의 권고를 선택함 | 확정 |
| D-028 | 2026-11-10 | "시연용 포인트 +10,000P" 링크(D-024)를 제출 빌드에도 **유지**한다. 정차 중에만 동작하고 "시연용" 표기를 그대로 둔다. 제출 설명서에 시연 보조 기능임을 적는다. 사용자가 "너가 다 해도 돼"라고 위임해 에이전트가 정했으며, 사용자가 원하면 제거로 바꾼다 | 심사자가 APK를 에뮬레이터에서 실행하면 보유 포인트가 적어 교환·바코드 화면을 볼 수 없다. 시연 영상 촬영에도 필요하다. 임의 지급으로 오해받지 않도록 "시연용"을 화면에 분명히 표시한다 | 확정 |
| D-027 | 2026-11-09 | 정차 화면에 "가까운 사용처" 카드를 추가한다. FusedLocation의 현재 위치로 종류별(주유소·충전소·정비소) 가장 가까운 1곳과 거리를 보여준다. **장소 데이터는 시연용 예시이며 실제 영업점이 아니다**(화면에 "시연용 예시"를 표시). 주행 중에는 계산·표시하지 않는다. 실제 서비스에서는 내비 POI 검색이나 제휴사 데이터로 교체한다 | 기획서의 "주변 주유소·충전소·블루핸즈 위치 연동"을 만들지 않으면 그 문장을 삭제해야 한다. 실제 영업점 위치 데이터는 공개 API가 없어서 지어내지 않고 예시임을 밝힌다. 사용자가 개발 완료 후 기획서 수정 요청을 하기로 했다 | 확정 |
| D-026 | 2026-11-09 | 공모전에 제출하는 GitHub 저장소는 현재 저장소가 아니라, 작성자 이름·이메일을 중립 값으로 설정한 **제출용 새 저장소**로 만든다. 만드는 시점은 제출 직전이다. 현재 저장소의 이력은 바꾸지 않는다 | 현재 저장소의 커밋 작성자 정보가 제출 규정(개인정보·실명·계정 정보 노출 금지)에 맞지 않는다. 이력 재작성은 되돌릴 수 없어서 새 저장소 방식을 택했다 | 확정 |
| D-015 | 2026-11-09 | 빌드 JDK는 Eclipse Temurin 17을 쓴다. Android Studio 내장 JBR(JDK 25)은 쓰지 않는다 | Vehicle 샘플이 JDK 17을 요구하고, Gradle 8 계열은 JDK 25에서 실행되지 않을 가능성이 큼 | 확정 |

## 9. 남은 과제

현재 상태 기준으로 정리한 목록이다. 끝난 일은 변경 이력에 있다.

### 검증 (에뮬레이터)

- [x] **TTS 재생:** `speak(text, true)`일 때 `onStart`/`onDone`이 모두 오는 것을 확인했다. 실제 소리를 듣는 확인은 남아 있다 (V-08)
- [x] **경로 안내 중 제한속도:** 경로 요청은 성공했으나 안내 중에도 값이 `0`이었다. 에뮬레이터에서는 확인할 수 없어 실차 확인 사항으로 남긴다 (V-06). 앱은 `0`이면 제한속도 정보 없음으로 처리한다
- [x] **내비 위치와 `geo fix`:** 따라 움직이지 않음을 재현했다 (V-13)
- [x] **권한(설치 시점), 2026-11-10 정정:** 2026-11-09 기록("`-g` 없이 설치해도 CAR_SPEED가 모두 `granted=true`, 일반 권한으로 자동 허용")은 **틀렸다.** 이전 설치 위에 덮어써서 기존 허용이 남은 결과였다. 앱을 삭제하고 새로 설치하면 설치 시점에는 `CAR_DRIVE`, `NAVI_ROUTE`, `TTS_SERVICE`, `CAR_POWERTRAIN`이 자동 허용되고, **`android.car.permission.CAR_SPEED`와 `pleos.car.permission.FUSED_LOCATION`은 런타임 권한이라 `granted=false`**다. 허용하기 전에는 속도를 읽지 못한다. 앱이 첫 실행에서 두 권한을 요청하도록 고쳤고(`MainActivity`), 새로 설치한 뒤 대화상자에서 둘 다 허용하면 속도·위치가 동작함을 주행 시나리오로 확인했다(리포트 0.83km, 점수 94). 대화상자 문구는 시스템이 "알 수 없는 작업"으로 표시한다(권한 라벨 없음)
- [ ] **권한(심사):** Pleos App Market 심사에서 `android.car.permission.*` 선언이 허용되는지는 심사 가이드와 FAQ에서 확인한다 (V-02). 에뮬레이터 결과는 심사 결과와 별개다
- [x] **바코드 화면:** 막대가 대화상자 폭 안에 들어오고 코드와 "시연용 코드" 문구가 정상 표시됨을 캡처로 확인했다 (2026-11-10)
- [x] **ANR 재현 여부:** 부팅 후 7분 이상 지난 상태에서 주행 시나리오를 3회 실행해 ANR 0건이었다(2026-11-10). 부팅 직후 과부하 가설과 모순은 없으나, 부팅 직후에 다시 시험해 재현하지는 않았으므로 원인은 확정하지 못했다. 시연 전에는 부팅 후 2분 이상 기다린다
- [ ] **글자 크기:** 밀도 보정을 앱 창 폭 비례로 바꾼 뒤 정차 화면의 가독성 확인
- [x] **앱이 가려져도 주행 기록이 이어지는지 (V-16):** 처음 시험(홈 키 후 20초, 뒤로 키 후 20초 관찰)에서는 기록이 이어지는 것으로 보였으나, 관찰 시간이 짧아 **잘못된 결론이었다.** 내비게이션 앱을 앞으로 띄워 우리 앱을 가리면 시스템이 약 10초 뒤 프로세스를 얼려(`ActivityManager: freezing ... com.drivecash.app`) 거리가 늘지 않았다(0.265km에서 90초간 정지). 포그라운드 서비스(`DriveKeepAliveService`)를 추가한 뒤 같은 시험에서 2분 동안 얼지 않고 거리가 0.38km → 1.77km로 늘었으며, 가려진 채로 P단 전환 시 리포트도 생성됐다(1.86km, 합계 217P). 시스템이 메모리 부족으로 프로세스를 종료하는 경우는 시험하지 못했고, 그때 진행 중인 주행은 사라진다(저장은 주행이 끝날 때만 한다)
- [ ] **자정 경계 시험 불가:** 날짜가 바뀌면 출석·오늘 주행거리 상태를 다시 계산하도록 고쳤으나(`MainViewModel.tick`, `RewardStore.refresh`), 에뮬레이터 시계를 바꿀 수 없어(root 불가) 실행해 보지 못했다. 컴파일과 기존 시나리오 동작만 확인했다
- [ ] 엔진 단위 시험은 없다. 엔진이 순수 Kotlin이라 필요하면 JVM 단위 시험으로 추가할 수 있다

### 제품

- [ ] D-020의 포인트·안전운전 기준값은 가정이다. 실차 데이터가 생기면 조정한다
- [x] 시연용 포인트 지급 링크는 제출 빌드에 남기기로 했다 (D-028)
- [x] 제출용 릴리스 APK 서명 설정: 서명 키는 저장소 밖(사용자 홈 폴더)에 두고, 비밀번호는 사용자 Gradle 설정(`~/.gradle/gradle.properties`)에서 읽는다. 값이 없으면 서명 없는 APK가 만들어진다. 서명 인증서에는 이름·이메일을 넣지 않았다(`CN=DriveCash`). `gradlew assembleRelease`로 만든 APK(약 40MB)를 새로 설치해 주행 시나리오까지 확인했다 (2026-11-10). 키를 잃어버리면 같은 서명으로 업데이트할 수 없으니 키 파일을 백업해 둔다
- [x] 검증 전용 권한 `NAVI_ROUTE_SEARCH`를 매니페스트에서 제거했다 (V-06)
- [ ] 단계 2 로드맵: 실제 POI 기반 주변 주유소·충전소·정비소 안내(시연용 고정 목록은 구현 완료, D-027), 횡가속도 등 추가 신호, 마이현대·블루핸즈 실제 연동 (V-10, V-11)

### 제출 (공모전)

- [ ] 기획서 5장·3장·4장의 SDK 활용 문장을 실제 구현에 맞게 수정할지 결정 (D-011, 5장 참고). 대조표는 로컬 제출 메모에 있다
- [ ] **화면 방향 문의 (D-029):** 가로 레이아웃을 유지하기로 했다. FAQ #28의 "Portrait 기준" 의미를 공식 채널에 문의하고(문안은 `docs/SUBMISSION.md` 7장), 답에 따라 좁은 폭용 레이아웃이 필요한지 다시 정한다. 답이 없으면 현재 상태로 제출한다. 문의는 사용자가 직접 한다
- [x] 기획서 담당자 전달 문서 작성: `참고자료/기획서_수정_전달문서.md` (git 제외, 개인정보 없음). 사용자가 담당자에게 전달한다
- [ ] 시연 영상 촬영 (선택 제출, 가산점). `tools/record-demo.ps1`(에뮬레이터 안 `screenrecord`)을 시험했다: 1920x1080, 12Mbps로 약 10MB 파일이 생성되고 `moov` 박스까지 정상 종료됐다(2026-11-10). 영상을 재생해 내용을 확인하지는 못했고 재생 길이·해상도도 측정하지 못했다. 촬영 전에 직접 재생해 본다. `screenrecord`는 1회 최대 180초이며 주행 시나리오는 약 3분이라 구간을 나눠 찍는다
- [ ] 제출용 새 GitHub 저장소 생성 (D-026): 중립적인 작성자 정보로 소스를 올리고, `참고자료/`와 로컬 제출 메모가 포함되지 않았는지, 문서에 개인 경로나 계정 정보가 없는지 확인한 뒤 공개한다
- [ ] 2차 PT 심사 기준 공지 확인 (심사·시상 페이지). 2026-11-10 확인 시 "2차 PT 심사 기준"과 "수상팀 전시 준비 기간 준수사항"은 여전히 "추후 안내 예정"이었다. 제출 직전에 다시 확인한다
- [ ] 접수 이메일 제출 (접수양식의 개인정보·서명 항목은 제출자가 직접 작성). 마감 2026-11-15 23:59

## 10. 참고 링크

| 용도 | URL |
|---|---|
| 공모전 개발 환경 안내 | https://appdevchallenge.auto-hmg.io/guide/development |
| 공모전 개요 | https://appdevchallenge.auto-hmg.io/guide/overview |
| 심사 기준과 시상 | https://appdevchallenge.auto-hmg.io/guide/judging-awards |
| Pleos Connect 에뮬레이터 설치와 개발 환경 설정 | https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator |
| Pleos Connect SDK 개요 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ |
| Vehicle SDK 샘플 | https://github.com/Pleos-Playground/pleos-sample-vehicle-app |
| NaviHelper SDK 샘플 | https://github.com/Pleos-Playground/pleos-sample-navi-app |
| FusedLocation SDK 샘플 | https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app |
| Pleos Playground 개발자 포털 | https://developers.pleos.ai/ |
| 개발자 FAQ와 Q&A | https://github.com/Pleos-Playground/pleos-playground-api/discussions |
