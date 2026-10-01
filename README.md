# fix/compose-and-swagger

develop에서 발견한 버그 2개를 수정한 브랜치입니다. (변경 파일 2개)

## 수정 내용

### 1. docker-compose.yml — DB가 실행되지 않던 문제
- 증상: `docker compose up` 실행 시 `additional properties 'ai' not allowed` 오류로 DB가 뜨지 않음
- 원인: 파일 끝에 Spring 설정(`ai: server: base-url: ...`)이 들어가 있었음. docker-compose 최상위에는 `services`, `volumes` 같은 정해진 키만 올 수 있음
- 수정: 해당 3줄 삭제. 같은 값은 이미 `application.properties`의 `ai.server.base-url`에 있어서 영향 없음

### 2. swagger — API 문서 화면이 열리지 않던 문제
- 증상: `/swagger-ui/index.html` 404, `/v3/api-docs` 500 (`NoSuchMethodError`)
- 원인: `springdoc-openapi 2.0.2`는 Spring Boot 3용인데 프로젝트는 Spring Boot 4.1.1
- 수정: `build.gradle`에서 `springdoc-openapi-starter-webmvc-ui`를 `3.1.1`(Boot 4 지원)로 변경
