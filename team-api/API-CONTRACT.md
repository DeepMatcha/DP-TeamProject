# DP Life Game — API 구현 계약 1.0

**상태: 사용자 최종 승인에 따라 선언 확정.** 이 문서와 `life-api/src/main/java/com/holub/life/api/`의 Java 선언을 Phase 1·2 공통 구현 기준으로 사용한다. 선언 확정은 engine·plugin 구현 완료 또는 통합 테스트 완료를 뜻하지 않는다.

기준 소스: `DeepMatcha/DP-TeamProject`의 `58aac8de7bb05bfb86800ee24d3886338bb74eb0`에 올라간 `team-api/`. 이전 RC1 및 11월 6일 초안의 충돌하는 설명은 이 문서로 대체한다. 목표 환경은 **JDK 25**다.

## 1. 이번에 확정한 변경

| 항목 | 최종 계약 |
|---|---|
| Lifecycle 등록 | `registerLifecycle(LifecycleManager)`; 타입 인자 없음. 등록 한 번이 보드 전체 stage 하나 |
| 규칙의 타입 처리 | **각 규칙의 책임**. 호스트가 자동 타입 분기·미지원 타입 보존을 강제하지 않음 |
| BoardEffect | 인터페이스와 등록 기능 삭제. 영양분·폭발도 주변 상태 → 자기 다음 상태로 표현 |
| 난수 | `StepContext(long generation, RandomGenerator random)`; 생성·seed·알고리즘·진행 관리는 호스트 |
| 순서 | `SimulationPlan.plugins()`의 목록 순서. 숫자 우선순위/동률 없음. plugin 내부 같은 endpoint는 등록 순서 |
| ID | 기존 `id()`·`formatId()` 유지. 등록 정보 및 singleton ↔ ID 조회 추가. 별도 `version()` 추가 없음 |
| MVC·재생 | View 입력 → Controller → Model commit → listener. 자동 진행·정지·속도는 호스트 |
| Controller | 아래 2절의 완료/실패·속도·session·registry·오류 경로로 확정 |
| 코어 타입 접근 (1.1) | `CoreCellTypes`의 ID 상수와 `PluginRegistrar.cellTypes()` 추가. 호스트는 코어 타입을 이 ID로 plugin보다 먼저 등록. 3절 참고 |

## 2. ControllerPort — 공개 선언

```java
public interface ControllerPort {
    CompletionStage<BoardState> step();
    CompletionStage<BoardState> apply(BoardPatch patch);
    CompletionStage<Void> setRunning(boolean running);
    CompletionStage<Void> setTickIntervalMillis(int milliseconds);
    CompletionStage<PlaybackState> playbackState();
    CompletionStage<GameSnapshot> session();
    CompletionStage<BoardState> restore(GameSnapshot snapshot);
    CompletionStage<Void> configure(SimulationPlan plan);
    RegistryView registry();
    void addErrorListener(HostErrorListener listener);
    void removeErrorListener(HostErrorListener listener);
}
```

`CompletionStage`는 **완료·실패 전달 방식**이며 비동기 worker나 특정 스레드 구조를 강제하지 않는다. 단일 스레드에서 이미 완료된 stage를 반환해도 된다. 호스트는 한 session의 편집·계산·복원·설정 변경을 일관된 순서로 처리한다. 동시에 들어온 요청의 수락 순서는 호스트가 정하되, 이미 완료된 요청보다 나중에 수락한 요청이 앞선 결과를 덮어쓰는 식의 stale commit은 허용하지 않는다. 의도적으로 여러 번 호출한 `step()`은 각각 한 번 진행하며 임의로 중복 제거하지 않는다.

| 메서드 | 동작·완료 시점 |
|---|---|
| `step()` | 전체 lifecycle chain을 한 번 계산. generation은 전체 tick당 한 번 증가. 최종 board commit 및 Model 통지 발행 후 완료 |
| `apply(patch)` | 직접 편집. 중복 좌표 LWW 후 공통 구조 검증, 한 번에 commit. generation·seed는 유지 |
| `setRunning(b)` | 자동 진행 시작/정지. 같은 원하는 상태를 반복 설정해도 timer가 중복 생성되지 않음. 정지 완료 후 새 자동 tick을 시작하지 않음 |
| `setTickIntervalMillis(ms)` | 양수 ms만 허용. 기존 실행 여부는 유지. 원본 속도 프리셋 500/150/70/30ms를 UI에서 사용할 수 있음 |
| `playbackState()` | 해당 요청 처리 시점의 `PlaybackState(running, millisecondsBetweenTicks)` |
| `session()` | board·plan·pluginVersions를 같은 시점에서 캡처한 전체 **simulation** snapshot. 조회를 위해 자동 진행을 정지할 필요는 없음 |
| `restore(snapshot)` | 모든 등록 ID·plan·공통 구조·호환성을 먼저 검증. 성공 시 전체 simulation session을 원자적으로 교체, **정지 상태**로 둠. 현 tick 간격은 유지 |
| `configure(plan)` | 다음 연산 경계에서 plugin 순서/활성 상태/Accessor/옵션을 검증·적용. board·generation은 변경하지 않음. 호스트가 지원하지 않는 옵션은 실패로 보고 |
| `registry()` | 등록 모듈 및 메타데이터의 읽기 전용 catalog. 같은 수명 동안 등록 singleton을 유지 |

이 표의 실행 세부값은 이번 추가 선언을 위한 기본 정책이다. `GameSnapshot`은 View 데이터나 scheduler의 running/interval을 포함하지 않는다. 재개 후 같은 계산을 계속하기 위한 simulation 상태와 실행 속도는 구분한다. `restore()` 성공 시 `ModelListener`에 새 board를 알린다. plan·재생 상태만 바뀐 경우에는 완료 결과/조회 메서드로 확인하며 불필요한 board 변경 이벤트를 만들 필요는 없다.

**오류 처리:** 요청 처리 실패는 반환 stage의 exceptional completion으로 전달한다. 실패한 계산·편집·복원·설정은 부분 commit하지 않으며 이전 simulation 상태를 보존한다. 호스트가 자동 호출한 tick의 실패에는 호출자가 없으므로 `HostErrorListener.onError(HostError)`로 알리고 자동 진행을 정지한다. 직접 요청한 실패를 같은 listener에도 중복 전송할 필요는 없다. 잘못된 null 인자 같은 호출 자체의 프로그래밍 오류는 즉시 예외를 던져도 된다.

리스너와 completion callback은 Swing EDT 실행을 보장하지 않는다. View adapter가 필요한 UI dispatch를 수행하고 UI 스레드에서 대기하지 않는다. Model의 commit과 통지 발행은 UI가 실제로 paint를 마치는 것과 별개다. View/listener의 예외가 이미 완료된 commit을 취소하거나 다른 listener 통지를 막아서는 안 된다. View는 `detach()`에서 등록한 listener를 제거한다. `ViewPlugin.onError()`는 추가하지 않는다.

## 3. Model·타입·등록 정보

`BoardState`는 immutable 2D snapshot이며 `BoardSize`, generation, seed, row-major `CellState` 목록을 소유한다. 타입 객체는 별도 singleton이고, 좌표별 `CellState.properties`는 immutable `Map<String,String>`으로 유지한다. 속성의 의미와 유효성은 plugin 책임이며 호스트용 속성 검증 SPI는 추가하지 않는다. 호스트의 null·좌표·크기·등록 타입 같은 공통 구조 검사는 유지한다.

코어 타입은 호스트, 확장 타입은 각 plugin이 concrete class의 `static final INSTANCE`로 소유한다. 공통 API에 기본 타입 **객체** 상수를 추가하지 않는다. 대신 plugin이 코어 타입에 접근할 수 있도록 ID 문자열과 조회 경로만 공유한다(1.1 추가).

```java
public final class CoreCellTypes {
    public static final String EMPTY_ID = "core:empty";
    public static final String ALIVE_ID = "core:alive";
    public static CellType empty(CellTypeLookup types);   // 미등록이면 IllegalStateException
    public static CellType alive(CellTypeLookup types);
}
// PluginRegistrar
CellTypeLookup cellTypes();
```

호스트는 빈 칸과 살아 있는 칸의 singleton을 위 ID로 등록하며, **어떤 plugin의 `register()`보다 먼저** 등록한다. `PluginRegistrar.cellTypes()`는 등록된 타입의 읽기 전용 live 조회이고 호스트 수명 동안 유효하다. Lifecycle 규칙은 `figureNextState`에서 조회할 수 없으므로, plugin이 `register()`에서 받은 조회를 규칙에 넘겨 죽은 셀의 다음 상태(빈 칸) 같은 코어 상태를 만든다. 메모리에서의 타입 비교는 같은 등록 객체에 대한 `==`; 파일은 안정적인 문자열 ID를 사용한다. 등록되지 않은 객체는 다른 등록 객체와 `id()`가 같아도 동일 타입으로 간주하지 않는다.

```java
public interface CellTypeLookup {
    Optional<CellType> resolve(String typeId);
    Optional<String> idOf(CellType type);
}
public interface RegistryView extends CellTypeLookup {
    List<PluginInfo> plugins();
    List<RegistrationInfo> registrations();
    Optional<AccessorFactory<GridPos>> accessor(String id);
    Optional<InputTool> inputTool(String id);
    Optional<SnapshotCodec> snapshotCodec(String formatId);
    Optional<PatternCodec> patternCodec(String formatId);
    Optional<ViewPlugin> view(String id);
}
```

`PluginInfo(id, metadata)`의 `metadata["version"]`에서 필요한 버전 정보를 관리한다. `LifePlugin.version()`이나 별도 버전 조회 API는 없다. `GameSnapshot.pluginVersions`는 이 메타데이터를 저장한 값이며 새 식별 체계가 아니다. 로더가 메타데이터를 공급한다; 임의의 버전 문자열을 만들어 호환성을 주장하지 않는다.

`RegistrationInfo(pluginId, point, id, index)`는 API point별 등록 정보를 나타낸다. `index`는 같은 plugin·같은 point 안의 0부터 시작하는 등록 순서다. 기존 ID는 그대로 쓰고, ID 메서드가 없는 lifecycle만 호스트가 `pluginId + "#lifecycle/" + index`로 표시용 등록 ID를 부여한다. 실행 순서는 이 문자열이 아니라 plan의 plugin 순서와 index로 결정한다. Plugin ID는 전역 유일, 모듈 ID는 해당 API point 안에서 유일해야 한다. 충돌 등록은 거절한다. catalog는 disabled plugin도 포함하고 불변 목록을 반환한다. 조회 실패는 `Optional.empty()`다.

## 4. Accessor·NeighborBoard

`AccessorFactory<T>.bind(snapshot)`은 읽을 source를 고정한다. **활성 Accessor는 하나**이고 중첩 변환은 지원하지 않는다. v1 engine의 canonical 좌표는 `GridPos(x,y)`다. 새로운 좌표 표현 타입의 정의가 임의의 3D topology까지 자동 지원한다는 뜻은 아니다.

`NeighborBoard`는 보드의 최적화 구역이 아닌 중심 주변의 읽기 전용 공간이다. manager의 `distance`는 생성 시 고정하며 plugin마다 달라도 된다. 기본 거리 r은 Moore/Chebyshev 거리다. `Integer.MAX_VALUE`는 전체 canonical board를 조회한다는 sentinel이며 무한 범위를 반복하지 않는다.

- `center()`와 `atOffset(0,0)`은 같은 중심 셀을 반환한다.
- 다른 offset은 range 검사 후 Accessor에 경계/변환 해석을 위임한다. 범위 밖 또는 닿지 않는 일반 보드 밖은 `Optional.empty()`다.
- `cells()`는 변환 후 canonical 좌표로 중복 제거하고 중심을 제외하여 `(y,x)` 오름차순의 불변 목록을 반환한다. 개별 offset 조회에서는 여러 offset이 같은 실제 칸을 가리킬 수 있다.
- Toroidal은 음수 좌표도 wrap하며, 2×2 torus의 이웃 수는 3, 1×1은 0이다. 전체 range의 목록은 자기 칸을 제외한 canonical cell 집합이다.
- 내부 8×8 partition과 dirty-edge 최적화 정보는 노출하지 않는다.

## 5. Lifecycle·순서·난수

```java
void registerLifecycle(LifecycleManager manager);
public abstract CellState figureNextState(NeighborBoard neighbors, StepContext context);
public record StepContext(long generation, RandomGenerator random) { }
```

등록 하나가 stage 하나다. **모든 칸**에 대해 현재 stage의 동일한 input snapshot을 읽고 자기 칸의 다음 상태 하나를 반환한다. 다른 칸의 상태나 호스트를 직접 변경하지 않는다. 음식 섭취·터질 예정·소멸·주변 생성은 상태를 추가하고 각 셀이 그 상태에 반응하도록 구현한다. `BoardEffect`나 lifecycle 출력의 patch/LWW 병합은 없다.

예를 들어 plugin 배치가 `[A, B, C]`이고 lifecycle 제공자가 A와 C이면 `[A의 등록 순서, C의 등록 순서]`로 실행한다. A의 전체 결과 B1을 C가 읽으며 셀별 A→C interleaving을 하지 않는다. 중간 board는 View에 publish하지 않는다. 한 tick의 모든 stage가 끝난 결과만 commit하고 generation을 한 번 증가시킨다.

규칙은 타입·이웃 수·속성 중 어떤 것을 먼저 검사해도 된다. 미지원 타입을 유지할지, 지울지, 변환할지 모두 규칙 책임이다. 기본 Conway가 장애물을 지우는 조합도 API가 자동 보정하지 않는다. UI에는 조합/순서에 따른 결과 차이를 알린다. 기본 Conway를 교체하려면 기본 stage를 비활성화한다. 다수의 진화 규칙을 활성화하면 실제로 연속 변환된다.

**난수 계약:** 동일한 보드(전체 상태·generation·seed), plugin 구현/설정, plugin/stage 순서, Accessor 및 편집 입력이면 동일 결과를 보장한다. `calcNext(source, plan)`을 다시 호출한 결과가 숨은 RNG 사용 이력에 따라 달라져서는 안 된다. Plugin은 호스트가 제공한 `context.random()`만 사용하며 이를 저장·재설정하거나 벽시계/global RNG를 사용하지 않는다.

호스트가 generator 구현·seed·알고리즘·수명·호출 배분을 담당한다. 고정 순회나 좌표 기반 파생 난수는 구현 예시일 뿐 특정 방식은 강제하지 않는다. Stateless 계산 및 Snapshot 복원을 위해 호스트는 저장된 seed·generation·plan에서 필요한 난수 진행을 재구성할 수 있어야 한다. 보존하지 않은 전역 RNG cursor만으로 계산하는 구현은 계약 위반이다. 같은 snapshot 복원 뒤 같은 입력으로 계속 진행하면 같은 결과여야 한다. 서로 다른 plugin/호스트 버전의 코드가 같은 결과를 낸다는 뜻은 아니다.

순서 보장은 프로그램이 실제 수락한 edit/step 순서를 포함한다. 사용자 클릭이 서로 다른 generation에 들어간 두 실행을 같은 입력이라고 취급하지 않는다. 성능 최적화를 적용해도 계약상 결과는 보존해야 한다.

## 6. 직접 편집·저장

클릭·브러시·pattern 배치는 `BoardPatch`를 사용한다. Patch는 canonical 좌표에 대한 **값 할당**이다. 입력 도구가 필요 시 Accessor로 목적지 좌표를 먼저 해석한다. 같은 목적지에는 목록의 마지막 값만 남기고 최종 변경값을 검증한 후 원자적으로 적용한다. Lifecycle에는 이 충돌 규칙을 적용하지 않는다. 단순 clear는 모든 칸을 호스트의 empty 타입으로 바꾸는 patch로 표현할 수 있으므로 별도 clear 메서드를 필수로 두지 않는다.

```java
// SnapshotCodec
String formatId();
void save(GameSnapshot snapshot, OutputStream out, CellTypeLookup types) throws IOException;
GameSnapshot load(InputStream in, CellTypeLookup types) throws IOException;
// PatternCodec
String formatId();
void save(Pattern pattern, OutputStream out, CellTypeLookup types) throws IOException;
Pattern load(InputStream in, CellTypeLookup types) throws IOException;
```

전체 simulation 저장은 `GameSnapshot(board, plan, pluginVersions)`, brush/preset 저장은 상대 좌표 `Pattern`이다. Codec은 파일/stream 변환만 하고 Model commit은 하지 않는다. Controller의 registry를 codec에 넘겨 저장 시 객체 → ID, 로드 시 ID → 기존 singleton으로 매핑한다. 미등록 타입 ID나 손실 없는 저장이 불가능한 형식은 `IOException`으로 보고한다. stream의 소유·close 책임은 호출자다.

기존 `.life`는 살아 있는 좌표만 저장하므로 legacy Pattern adapter로 호환하며, 확장 타입·속성·session 설정이 보존되는 full snapshot이라고 취급하지 않는다. concrete CSV/JSON schema는 해당 codec 구현에서 정한다. `session()` → 선택 codec의 save, codec load → `restore()` 흐름을 사용한다. restore 실패 전 현재 board를 clear하지 않는다.

## 7. 구현·검증 경계

변경 범위는 `team-api/`의 문서와 공개 선언·예제다. 원본 `src/`, Gradle 앱 설정, 실제 Phase 1/2 구현은 이 API 확정 작업에 포함하지 않는다. 개인별 Phase 담당자는 정하지 않는다.

Phase 1은 실제 engine·host·MVC와 fake plugin으로, Phase 2는 실제 plugin과 fake Neighbor/host로 기대 결과를 검증한다. API-only 컴파일은 통합 동작 또는 JUnit 통과가 아니다. 전체 보드 평가를 기본으로 하고 최적화는 호스트 내부에서 필요한 만큼 구현한다. 기존 코드의 오류를 그대로 보존할 필요는 없지만 정상 작동하던 기능은 유지한다.

과제명세서는 Java, 기존 기능 유지, JUnit 검증, 테스트 목적·결과 스크린샷, 실제 설계 비교·선택 근거를 요구한다. Model/UI 독립 테스트는 권장이다. 자세한 최소 검증 범위는 [TEST-CONTRACT.md](TEST-CONTRACT.md)를 따른다. 추가 승인 회의·release/tag 절차는 요구하지 않는다.

```sh
# 목표 환경: JDK 25
sh team-api/compile.sh
java -cp team-api/build/api:team-api/build/smoke ApiValueSmoke
```

`ApiValueSmoke`는 DTO·시그니처 보조 검사이며 과제용 JUnit이나 실제 host 통합 검증을 대체하지 않는다. Windows의 Java classpath 구분자는 `;`다. 더 낮은 JDK에서 `JAVA_RELEASE=21`을 지정한 실행은 호환성 점검일 뿐 JDK 25 검증으로 보고하지 않는다.

## 8. 원본에서 이동할 책임

`Resident.figureNextState` → Lifecycle, `Neighborhood`의 주변 탐색 → Neighbor/Accessor, `Universe`의 입력·계산·paint·IO → MVC와 codec, `Clock`의 timer/메뉴 결합 → 호스트 scheduler와 View로 나눈다. 이 내부 클래스들을 Phase 2 plugin에서 import하지 않는다.

기존 회의 요약·참여자 메모와 후속 승인에 근거한 설계이다. 원본 코드: [DeepMatcha/DP-TeamProject](https://github.com/DeepMatcha/DP-TeamProject). Java 표준 참조: [CompletionStage](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletionStage.html), [RandomGenerator](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/random/RandomGenerator.html). 과제 원문: `DP Term Project 2026 Life Game - updated-4.pdf`, 1–2쪽.
