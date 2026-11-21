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
