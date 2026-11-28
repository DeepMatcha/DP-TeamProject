# 배포본 검증

JDK 25에서 저장소 루트를 기준으로 실행한다. 기본 앱은 `build/libs/game-of-life.jar`와 그 옆의 `plugins/`를 사용한다. 경로를 직접 고르려면 JVM에 `-Dlife.plugins.dir=/경로`를 전달한다.

```sh
./gradlew installDist
harness=verification/desktop/DesktopMergeAcceptance.java

# 생성된 JAR와 인접한 plugins/를 사용한다.
java -cp build/libs/game-of-life.jar "$harness" providers
xvfb-run -a timeout 60s java -cp build/libs/game-of-life.jar "$harness" plugins

# 설치 배포본의 lib/와 plugins/를 사용한다.
app_libs='build/install/game-of-life/lib/*'
java -cp "$app_libs" "$harness" providers
xvfb-run -a timeout 60s java -cp "$app_libs" "$harness" plugins

# 플러그인 JAR 없이 기존 조작을 확인한다.
empty_plugins=$(mktemp -d)
xvfb-run -a timeout 60s java -Dlife.plugins.dir="$empty_plugins" \
  -cp build/libs/game-of-life.jar "$harness" desktop
rmdir -- "$empty_plugins"
```

- `providers`는 실행 앱과 같은 `PluginDirectory`로 여섯 제공자를 찾아 로드한다. 테스트 클래스패스에 플러그인 JAR를 추가할 필요가 없다.
- `plugins`는 실제 앱 창에서 모두 기본으로 꺼져 있는지, toroidal·foodaging·obstacle 전환 후 보드와 세대가 그대로인지, 2D 창과 codec 메뉴가 활성 상태를 따르는지 확인한다. JSON 파일을 UI에서 저장한 후 보드·세대·플러그인 설정을 바꾸고, 불러온 세션의 화면과 재저장 파일을 비교한다. 이 검증은 OpenGL 없이 실행된다.
- `desktop`은 기존 셀 편집, 단일 세대 진행, 네 가지 자동 재생 속도, 정지, 초기화, 임시 `.life` 파일 저장·로드 및 종료를 확인한다.

Swing 검증에는 디스플레이가 필요하며 위 예시는 Linux의 Xvfb를 사용한다. 3D 창의 렌더링은 OpenGL을 사용할 수 있는 환경에서 따로 확인한다.
