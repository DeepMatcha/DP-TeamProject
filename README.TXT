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

Gradle 프로젝트 통합
--------------------
HolubLife 1.02의 Java 소스와 메뉴 테스트 리소스를 표준 Gradle 구조로 배치했다.
원본 Java 소스는 변경하지 않았으며, 배포본의 컴파일된 .class 파일은 제외했다.

소스: src/main/java/com/holub/
리소스: src/main/resources/com/holub/
실행 진입점: com.holub.life.Life

Windows에서 JDK 25로 빌드 및 실행:
  .\gradlew.bat build
  .\gradlew.bat run

실행 가능한 JAR:
  java -jar build/libs/game-of-life.jar

게임 조작:
  셀 클릭: 생존/죽음 전환
  Go > Tick (Single Step): 한 세대 진행
  Go > Agonizing / Slow / Medium / Fast: 자동 진행
  Go > Halt: 자동 진행 정지
  Grid > Clear / Load / Store / Exit: 초기화 / 불러오기 / 저장 / 종료

.idea/ 전체, IDE 출력, Gradle 캐시, 빌드 산출물, .class 파일은 Git에서 제외한다.
