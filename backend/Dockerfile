# Render 배포 전용 Dockerfile (로컬 개발은 Gradle 그대로 사용)

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 위해 빌드 설정 파일을 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*[!n].jar app.jar

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /workspace/app.jar app.jar
USER appuser

# Render가 PORT를 주입하며 application.yaml의 server.port=${PORT:8080}가 이를 사용
EXPOSE 8080
# 무료 인스턴스(512MB) 기준 힙 제한
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
