# DP Life Game 공통 API

공통 구현 기준은 [API-CONTRACT.md](API-CONTRACT.md)이다. 이 폴더는 **API 1.0 선언과 예제**이며 완성된 engine/host/plugin 구현이 아니다.

- `life-api/src/main/java/` — Java 공개 선언/공통 값 타입.
- `examples/` — API만 참조하는 host·plugin·View 컴파일 예제와 DTO 보조 검사.
- [TEST-CONTRACT.md](TEST-CONTRACT.md) — 구현 과정의 최소 JUnit/통합 검증 범위.

목표 JDK는 **25**다. repo root에서:

```sh
sh team-api/compile.sh
java -cp team-api/build/api:team-api/build/smoke ApiValueSmoke
```

Windows classpath 구분자는 `;`다. API 선언 확인용 하위 release 호환성 검사만 필요할 때 `JAVA_RELEASE=21 sh team-api/compile.sh`를 사용할 수 있으나 JDK 25 검증을 대체하지 않는다.

이전 초안에서 `BoardEffect`, `registerBoardEffect`, 타입 인자가 있는 lifecycle 등록, `PluginPriority`는 제거됐다. 목록 순서, 제공된 RNG, registry 양방향 조회, 완료·오류 및 session 제어 계약은 명세서를 따른다. 타입 간 상호작용은 **각 규칙의 책임**이다.

원본 앱 source/Gradle 설정은 이 API 폴더와 분리되어 있다. Phase 1·2는 같은 선언에 의존하고 실제 구현 테스트를 별도로 수행한다.
