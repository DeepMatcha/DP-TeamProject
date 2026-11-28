HolubLife.jar
Version 1.02
Released 16 Jul., 2005


version		description

1.01		Alexey Marinichev: Fixed the "stuck glider" problem in
			Neighborhood.java.

1.02		Added a workaround to a Swing bug that caused menus
			to be overwritten by the game. Swing just draws menus
			directly on the content-pane's main canvas, so these
			menus were being overwritten by the game itself if you
			selected a menu while the game was runing. Modified
			the Clock class to suspend any board updating when
			menu items are displayed.

Phase 1 실행
------------
JDK 25를 사용한다. Linux/macOS에서는 ./gradlew build, Windows에서는
.\gradlew.bat build로 빌드한다. ./gradlew run 또는 다음 명령으로 실행한다.

  java -jar build/libs/game-of-life.jar

호스트는 기본 Conway 규칙과 유한 보드로 독립 실행된다. 공통 API는
team-api/API-CONTRACT.md, 테스트 범위는 team-api/TEST-CONTRACT.md를 참고한다.

플러그인 설치와 실행
--------------------
./gradlew installDist로 통합 배포본을 만든 후 build/install/game-of-life/ 폴더를
원하는 위치에 통째로 복사한다. bin/game-of-life(Windows는 .bat)로 실행한다.
zip 배포본은 ./gradlew distZip으로 만들며, 압축을 푼 후 동일한 구조로 실행한다.

배포 폴더의 plugins/에 추가할 JAR과 런타임 의존 JAR을 놓고 게임을 시작한다.
호스트는 각 JAR의 META-INF/services/com.holub.life.api.LifePlugin 선언으로
제공자를 찾는다. 폴더가 비어 있어도 기본 Conway 게임을 할 수 있다.
개발용 ./gradlew run 및 build/libs/game-of-life.jar 옆의 plugins/는 빌드가
생성한다. 사용자가 별도로 유지할 폴더는 다음과 같이 지정할 수 있다.

  java -Dlife.plugins.dir=/path/to/plugins -jar build/libs/game-of-life.jar

처음에는 설치된 플러그인이 모두 꺼져 있다. Plugins 메뉴에서 체크하면 현재
게임에 적용되고, 체크를 해제하면 꺼진다. JAR을 추가·삭제한 경우에만 다시
시작한다. 토글할 때 기존 셀, 세대, 재생 상태는 유지된다.

  obstacle   벽을 고려하는 Conway 규칙. 기본 창의 Tools 메뉴에서 벽 도구를 고른다.
  foodaging  노화 및 먹이 규칙을 적용한다.
  toroidal   보드의 양 끝을 잇는다. 끄면 기본 게임의 유한 경계로 돌아간다.
  view2d     플러그인 셀을 표시하는 보조 2D 창을 연다.
  view3d     보드를 관찰하는 3D 창을 연다. 3D 그래픽 환경과 동봉된 라이브러리가 필요하다.
  codec      Grid > Session files에서 JSON 또는 binary로 전체 세션을 저장·복원한다.

obstacle과 foodaging은 동봉된 view2d JAR의 표시 기능을 사용한다. view2d의
체크를 해제해도 JAR이 설치돼 있으면 두 규칙을 기본 화면에서 사용할 수 있다.

게임 조작:
  셀 클릭: 생존/죽음 전환
  Go > Tick (Single Step): 한 세대 진행
  Go > Agonizing / Slow / Medium / Fast: 자동 진행
  Go > Halt: 자동 진행 정지
  Grid > Clear / Load / Store / Exit: 초기화 / 기존 .life 패턴 읽기·쓰기 / 종료

테스트:
  ./gradlew test                    헤드리스 환경에서는 Swing 테스트 제외
  xvfb-run -a ./gradlew clean test  Linux에서 Swing 테스트 포함
  sh team-api/compile.sh            공통 API와 독립 컴파일 예제 확인

기존 .life는 살아 있는 셀 좌표의 패턴을 저장한다. 전체 세션 저장과는 별개다.
