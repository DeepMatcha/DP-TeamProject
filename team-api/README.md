# DP 팀원 전달 패키지

1. `API-CONTRACT.md` — 합의된 동작 의미 + Java 선언/Phase1·2 분리.
2. `life-api/src/main/java/` — **컴파일 가능한 API 선언 초안** (Engine/Plugins 자체 구현 없음).
3. `TEST-CONTRACT.md` — 최소 JUnit/Stub 기대결과.
4. `examples/` + `compile.sh` — API 모듈에만 의존하는 Phase1/2 각각의 컴파일 확인.

## 사용
```sh
sh compile.sh
```
확인 환경: Java 21. 프로젝트 원본 빌드 설정/실제 게임 실행까지 검증한 것은 아니다.

## 중요
설계·동작 원칙은 사용자와 합의되었지만 **패키지 이름, 개별 함수 시그니처, DTO 필드까지 팀원 2인이 승인한 것은 아님**.
이 패키지를 일단 함께 읽고 Java API 선언에 합의한 뒤 각 Phase 구현을 시작한다.
기존 `DP-API-v0.1-RC1.zip`은 마지막 합의 전 버전이므로 사용하지 않는다.
