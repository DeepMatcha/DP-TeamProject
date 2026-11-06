# DP Team Project — Plugin API 계약서

- 기준일: 2026-11-06 KST
- 기준 코드: `DeepMatcha/DP-TeamProject`의 `main`, `fa1900d` (2026-10-29 Init)
- 대상 언어: Java. 제공된 `.java` 선언은 **컴파일되는 공통 초안**이며, 실제 engine/codec/plugin 구현은 포함하지 않는다.
- 상태: **아래 동작 원칙은 대화에서 확정됨**. **메서드 이름·패키지·보조 DTO 구성은 이 문서와 Java 선언을 팀원이 대조 승인하면 고정**한다. 아직 GitHub에 반영하거나 팀원 승인을 받은 것은 아니다.

## A. 구현의 기준 (확정된 합의)

**1. 기본 경계 / MVC**

- `View`가 사용자 입력을 받아 `Controller`에 전달한다.
- `Controller`는 현재 `Model(BoardState)`을 이용해 계산 engine을 호출하고, 정상 결과를 Model에 반영한다.
- View는 Model listener를 구독해 **commit된 최종 상태만** 표시한다.
- Tick scheduling은 Controller가 이용하는 별도 scheduler의 책임; `GameEngine`은 `calcNext(BoardState, SimulationPlan)`만 수행하고 현재 board, timer, history를 소유하지 않는다.
- 입력/출력은 Swing과 결합시키지 않는다. Console/TUI/network View도 같은 Model·Controller를 사용할 수 있어야 한다.

**2. 상태와 Cell 타입**

- `BoardState`는 크기, generation, seed, 각 좌표의 `CellState`를 보관하는 immutable snapshot이다.
- `CellState`는 **좌표마다 독립된** 상태와 확장 properties를 보관한다.
- 각각의 concrete `CellType`은 `static final INSTANCE` 같은 singleton을 둘 수 있다. 런타임 동일성 비교는 `==`를 사용한다.
- 단, **상위 클래스의 static 필드 하나가 상속 타입마다 자동으로 별도 생성되지는 않는다**. 따라서 singleton은 concrete type이 각각 정의한다.
- 직렬화에는 메모리 주소가 아닌 `CellType.id()` 등 안정적인 문자열 식별값을 사용하고 로드시 해당 singleton으로 매핑한다. `CellTypeLookup`은 이 로딩 경로에서만 필요한 최소 계약이다.
- `BoardState`의 좌표는 row-major 2D `GridPos(x,y)` 기준. 실제 저장은 2D를 유지한다.

**3. Accessor / Neighbor**

- `Accessor<T>.access(T)`는 **조회만** 담당한다. `AccessorFactory.bind(snapshot)`으로 읽을 스냅샷마다 생성한다.
- 실행 중 활성 Accessor 구현은 **하나**. 여러 Accessor의 중첩 합성은 이번 범위 밖이다.
- `LifecycleManager(distance)`의 distance는 생성 시 정하고 해당 manager 인스턴스에서 변경하지 않는다. manager마다 다른 값을 써도 된다.
- `NeighborBoard`는 중심 Cell, 거리, 상대 좌표 조회, **좌표 배치를 유지한 읽기 전용 주변 board view**를 제공한다. 기존 최적화용 8×8 구역은 plugin에 노출하지 않는다.
- 기본 거리 r은 Moore/Chebyshev 기준(`max(|dx|,|dy|) <= r`, 중심 제외). `Integer.MAX_VALUE`는 자기 칸을 제외한 전체 canonical board를 뜻한다.
- Toroidal은 `floorMod()`로 좌표를 해석한다. 조회 범위 밖은 `Optional.empty()`(일반 보드)일 수 있으며, 변환 이후 **canonical 좌표로 중복 제거하고 자기 자신은 제외한다**. `NeighborBoard.cells()`는 `(y,x)` 오름차순으로 정렬한다. 2×2 torus의 이웃 수는 3, 1×1은 0이다.

**4. Plugin 적용 우선순위 / lifecycle 동작**

- UI에서 활성 plugin마다 **전역 우선순위 숫자**를 한 번 정한다. 같은 순서를 **모든 API point**에 적용한다.
- 특정 API point에서는 해당 모듈을 제공하는 활성 plugin만 추린 배열을 **전역 우선순위 오름차순**으로 실행한다. 한 plugin이 같은 point에 여러 모듈을 내면 등록 순서를 유지한다.
- Lifecycle A는 **보드 전체 B0**를 읽어 중간 결과 B1을 구성한다. Lifecycle B는 완성된 **B1 전체**를 읽어 B2를 구성한다. 한 lifecycle stage 내부의 각 셀은 동일한 입력 B를 읽는다. B1은 View에 publish하지 않는다.
- 여러 lifecycle의 연속 적용은 규칙 합성이다. 기본 Conway를 **교체**하려면 해당 기본 stage를 비활성화해야 한다.
- `Accessor`는 복수 실행하는 API point가 아니라 선택형(하나 활성). Save/load는 사용자가 선택한 codec 하나로 처리한다.
- `BoardEffect`는 영양분 생성처럼 여러 좌표를 함께 바꾸는 작업을 위한 별도 point다. API point 간 실행 시점 자체는 전체 plugin 우선순위로 변경하지 않는다.

**5. 변경의 의미**

- 외부 `GameEngine.calcNext`는 입력 `BoardState`를 수정하지 않고 **새 결과**를 반환한다. 내부에서만 mutable 임시 버퍼를 사용할 수 있다.
- 여러 좌표를 수정하는 입력 도구/효과는 `BoardPatch`를 반환한다. 모든 edit은 **값 할당**이고 toggle 이벤트 나열이 아니다.
- 같은 canonical 위치에 여러 edit이 있으면 **목록상 마지막 값으로 덮어쓴다(last-write-wins)**. 실제 적용은 마지막 값들을 모두 검증한 다음 **원자적으로(commit 또는 전부 취소)** 한다.
- 무효한 좌표/타입/상태로 실패하면 Model은 그대로 유지하고 Controller가 오류를 전달하여 UI가 경고한다.

**6. 저장**

- `GameSnapshot`은 generation, seed, CellState들, Accessor/Plugin 선택 및 우선순위, 옵션, plugin 버전을 포함하는 **전체 재개 단위**다.
- `Pattern`은 상대 좌표 기반의 **브러시/프리셋** 자료이며 실행 session 전체를 저장하지 않는다.
- `SnapshotCodec`과 `PatternCodec`은 별도 Adapter SPI다. 기존 `.life`는 원본의 살아 있는 좌표만 저장하므로 full snapshot과 구분한다.
- 로드 시 파싱과 검증이 실패하면 기존 Model을 변경하지 않는다.

**7. 최적화·기존 코드**

- `Neighborhood`의 기존 activity/dirty-edge 최적화는 계산/이웃 조회와 결합돼 있다. **v1은 매 tick 전체 보드 계산을 기준으로 정확도를 확보**한다.
- 성능 최적화는 나중에 내부 구현으로 추가 가능하며 공개 plugin API로 강제하지 않는다.
- 기존 코드에 이미 존재하는 오류를 보존할 필요는 없으며, **기존 정상 기능 유지와 새 기능**을 검증한다.

## B. 전달하는 Java 선언 (팀원이 이름/형태만 최종 확인)

공통 package: `com.holub.life.api` (기존 `com.holub.life.Cell`과 별개)

| 모듈/API point | 외부 시그니처 |
|---|---|
| 기본 상태 | `CellType`, `CellState`, `BoardState`, `GameSnapshot` |
| 좌표 접근 | `Accessor<T>.access(T)`, `AccessorFactory<T>.bind(BoardState)` |
| 주변 조회 | `NeighborBoard.center()`, `.distance()`, `.atOffset(dx,dy)`, `.cells()` |
| 생존규칙 | `LifecycleManager.figureNextState(NeighborBoard,StepContext)` |
| 일괄 변경 | `BoardEffect.apply(...)`, `BoardPatch`, `InputTool.apply(...)` |
| Backend | `GameEngine.calcNext(BoardState,SimulationPlan)` |
| 저장 | `SnapshotCodec.save/load`, `PatternCodec.save/load` |
| Plugin 진입점 | `LifePlugin.register(PluginRegistrar)`, `PluginRegistrar.register...` |
| MVC 연계 | `ControllerPort`, `ModelPort`, `ModelListener`, `ViewPlugin` |

실제 Java 인터페이스·상세 필드는 `life-api/src/main/java/com/holub/life/api`를 따른다.
*중괄호 `{}`가 있는 것은 구현이 필요한 메서드/타입, `abstract ...;`와 `interface ...;`는 구현 의무를 나타낸다.*
이 API module 자체는 동작하는 Game of Life engine이 아니라 **공유할 선언만** 담는다.

## C. 원본 코드 변경 기준

| 현재 코드 | Phase 1의 리팩토링 경계 |
|---|---|
| `Resident.figureNextState()` | 생존 계산을 `LifecycleManager`로 추출 |
| `Resident`의 `amAlive`/`willBeAlive` | 좌표별 `CellState`와 immutable 다음 snapshot으로 분리 |
| `Cell` (계산 + redraw + click + Memento) | 상태/계산과 UI/입력/파일 입출력 분리 |
| `Neighborhood.figureNextState()` | 조회 범위/Accessor와 evaluation을 분리, 내부 최적화 비공개 |
| `Neighborhood.userClicked()` | `InputTool`이 만드는 patch를 Controller가 적용 |
| `Universe` | Board 생성/표시/입력/계산/IO를 MVC와 adapter로 분리 |
| `Clock` | Scheduler와 UI 메뉴 제어 분리 |
| `NeighborhoodState` | legacy pattern 입출력과 새 snapshot 포맷 분리 |

원본: https://github.com/DeepMatcha/DP-TeamProject/tree/main/src/main/java/com/holub

## D. Phase 1 · Phase 2 독립 구현의 최소 절차

1. **양쪽이 이 API 문서와 Java 선언 이름을 확인**하고, 공통 `life-api` 파일을 동일한 기준에 둔다.
2. **Phase 1:** `GameEngine`, `BoardState` 계산, MVC, 원본 Swing 연결, default Conway, 실제 patch commit/검증을 구현한다.
3. **Phase 2:** 외부 API만 참조해 custom rule, Toroidal, Aging, Brush, Obstacle, Nutrient, codec을 작성한다. 필요하면 fake host/model/neighbor로 단위 테스트한다.
4. 각각 **공통 API module만 의존해 컴파일**한다. 상대방 구현/`Resident`/`Universe`를 import하면 안 된다.
5. 실제 구현을 합쳐 JUnit 및 기존 기능 동작을 확인한다. 정식 release/tag/복잡한 PR 승인 절차는 별도 필수로 만들지 않는다.

## E. 테스트와 제출 관련

- 제출 명세서: Java, 기존 기능 유지, JUnit으로 기능 확장/설계 개선 검증.
- 보고서: 테스트 목적, 실행 결과(스크린샷), 설계 전후 비교 및 선택 이유를 기록한다. Model/UI 독립 테스트는 **권장**이며 필수 조건이라고 단정하지 않는다.
- `TEST-CONTRACT.md`에 서로의 fake/stub에서 확인할 최소 사례를 정의했다.
- `compile.sh`는 **API/Phase1/Phase2 독립 컴파일만 검증**한다. JUnit 통과 또는 실제 engine 동작 완료를 의미하지 않는다.

## 출처/한계

- 2026-10-31 팀 회의록과 2026-11-03 21:32 KST 회의 요약·스크립트.
- 두 참여자의 Strategy, Polymorphism, Adapter, User Input & View 메모.
- 사용자가 확인한 2026-11-05~06 설계 선택.
- `DP Term Project 2026 Life Game - updated-4.pdf`, p.1–2.
- GitHub `fa1900d` 소스 확인. **이번 패키지는 repository에 push하지 않았다.**
