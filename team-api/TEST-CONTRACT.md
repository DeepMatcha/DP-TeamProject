# DP 팀 JUnit/Stub 테스트 계약 (최소)

과제 원문은 기존 기능 유지, 새 기능 및 설계개선의 JUnit 검증, 테스트 목적·결과 스크린샷을 요구한다. 아래 사례는 **우리 팀의 제안된 테스트**이며 교수님이 사례 하나하나를 지정한 것은 아니다.

| ID | Phase 1 실제 구현 + fake plugin | Phase 2 실제 plugin + fake host/Neighbor |
|---|---|---|
| C01 | 기본 Conway: blinker/공존 패턴에 대한 1~3 tick 기대결과 | `DefaultLifecycle`의 이웃 상태별 생존 결과 |
| C02 | 입력 `BoardState` 불변 및 결과 generation 1회 증가 | singleton CellType 두 좌표의 age가 독립적으로 증가 |
| C03 | lifecycle plugin A→B: A가 만든 **보드 전체**를 B가 읽음 | fake Neighbor가 B1을 주었을 때 B가 기대한 결과 |
| C04 | 전체 UI 우선순위 필터링: API point별 A/C만 올바른 순서 | plugin 모듈 registration/우선순위 변경에 의존하지 않음 |
| C05 | Toroidal 2×2의 이웃 3개, 1×1의 이웃 0개 | Toroidal accessor가 음수 좌표도 올바르게 wrap |
| C06 | 한 좌표 중복 쓰기는 LWW; 무효 patch는 상태 변경 없이 오류 | brush/nutrient가 유효한 ordered patch 반환 |
| C07 | Controller 입력→Model 반영→fake View listener 통보 | fake host에 input tool·view plugin 연결 |
| C08 | snapshot 파일 저장/복원 / pattern 따로 로드 / 실패 시 보존 | 새 codec의 encode/decode 값 동일성 |
| C09 | 원래 동작하던 편집, tick, 재생/정지, Clear, Load/Store | 실제 구현 plugin의 기능 시연 및 JUnit 결과 |

- 각 테스트에 **목적, 입력 fixture, 기대 결과, JUnit 코드, 실제 실행 결과**를 남긴다.
- 원본의 이미 확인된 오류를 그대로 재현해야 할 의무는 만들지 않는다.
- `compile.sh`가 성공해도 이는 단지 **빌드 가능한 API 계약** 확인이며 JUnit 실행/실제 기능 검증이 아니다.
