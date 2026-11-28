# 배포본 병합 검증

JDK 25에서 저장소 루트를 기준으로 실행한다. Swing 화면 검증에는 디스플레이가 필요하며 Linux에서는 Xvfb로 실행할 수 있다.

```sh
./gradlew installDist
harness=verification/desktop/DesktopMergeAcceptance.java
xvfb-run -a java -cp build/libs/game-of-life.jar "$harness" desktop
java -cp 'build/install/game-of-life/lib/*' "$harness" providers
```

`desktop`은 배포 JAR에서 실제 게임 창을 열어 셀 편집과 단일 세대 진행, 네 가지 속도의 자동 재생, 정지, 초기화, 임시 `.life` 파일 저장·로드 및 종료를 확인한다.

`providers`는 **여섯 플러그인을 포함해 배포할 구성**을 평가한다. 실제 배포 클래스패스에서 발견한 제공자를 출력하고, 빠진 제공자가 있으면 실패한다. 플러그인 없는 기본 Conway 실행은 첫 번째 모드에서 별도로 확인한다. 제공자를 선택 설치하는 경우 두 번째 명령의 클래스패스에 설치된 JAR과 의존성을 포함한다.
