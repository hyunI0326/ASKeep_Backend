# 1단계: 빌드 (Java 21로 jar 파일 만들기)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
 && cp $(ls build/libs/*.jar | grep -v plain) app.jar

# 2단계: 실행 (빌드 도구는 빼고 jar만 실행)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# 무료 서버 메모리(512MB)에 맞춰 Java가 쓸 메모리를 제한
ENV JAVA_TOOL_OPTIONS="-Xmx300m -XX:+UseSerialGC"
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]