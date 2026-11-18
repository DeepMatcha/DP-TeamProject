# 최소 테스트 계약 — API 1.0

별도 복잡한 승인 절차를 만들지 않고 구현과 함께 JUnit 테스트를 작성한다. 아래는 팀 API의 검증안이지 교수님이 각각 지정한 사례는 아니다. 과제 제출 시 테스트 목적·입력·기대 결과·실제 결과 스크린샷을 남긴다. Model/UI 분리 검증은 권장 사항으로 유지한다.

| 범위 | 실제 구현에 요구되는 검증 |
|---|---|
| 기존 기능 | 기본 Conway의 탄생·생존·사망, stable pattern·blinker·glider, 편집·clear·tick·재생/정지·속도·기존 load/store |
| 상태 | 입력 불변, singleton과 좌표별 property 독립, 전체 tick당 generation 1회 증가 |
| Lifecycle | CellType 인자 없이 등록, 같은 stage가 동일 snapshot을 읽음, A의 **보드 전체 결과**를 B가 읽음 |
| 순서 | plan 목록 순서와 같은 plugin의 등록 순서. 미지원 타입 처리는 각 규칙의 기대 동작을 검증하고 호스트가 보존을 강제하지 않음 |
| Neighbor | center=atOffset(0,0), range, canonical 중복 제거·중심 제외·정렬, 음수 wrap, 2×2/1×1 torus |
| 난수 | 동일 source/plan/seed 재계산, 동일 실행 재현, snapshot 복원 후 계속 진행, 실패 후 재시도에서 숨은 난수 이력 영향 없음 |
| Food·Aging | 별도 BoardEffect 없이 주변 상태→자기 다음 상태. food 전이와 burst의 시간 순서 및 age를 plugin별 fixture로 검증 |
| 편집 | patch의 LWW와 원자성, 최종 값 공통 구조 검증, 잘못된 값이면 이전 Model 유지. Lifecycle 결과는 patch가 아님 |
| Controller | completion 정상/예외, 속도 변경·정지, 정지 완료 후 자동 tick 없음, 일관된 연산 순서. 특정 thread/queue 구현은 테스트 계약으로 강제하지 않음 |
| 오류·MVC | 정상 commit 후 listener, 직접 요청 실패는 completion, 자동 tick 실패는 오류 listener+정지, View 오류가 commit을 되돌리지 않음 |
| Session·등록 | configure 원자성, session 일관된 조회, restore 성공 후 정지·실패 시 기존 session 유지, ID↔등록 singleton 매핑 |
| Codec | snapshot/pattern 분리, 등록 lookup 사용, 미등록 ID/형식 실패, 기존 `.life` 호환 |

Phase 1은 실제 host에 fake plugin, Phase 2는 실제 plugin에 fake host/Neighbor를 연결한다. 양쪽 모두 stub인 테스트 성공을 실제 기능 구현 완료로 주장하지 않는다. 최종 결합 시 실제 구성으로 최소 통합·시연을 수행한다.

`compile.sh`는 API와 host/plugin/view 예제를 **각각 API만 의존하도록 컴파일**한다. `ApiValueSmoke`는 DTO·시그니처 검사일 뿐이며 engine/RNG 배분·thread·codec·실제 plugin은 검증하지 않는다. 과제용 JUnit은 실제 구현과 함께 별도로 실행·기록한다.
