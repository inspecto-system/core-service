# Image of core-service (T-207). Build: docker build -t inspecto/core-service .
# Stage 1 builds the jar with the Gradle wrapper; checks and tests run in CI, not here.
FROM eclipse-temurin:21.0.13_9-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY config config
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon --quiet bootJar && cp build/libs/*.jar /src/app.jar

# Stage 2: JRE only, no build tools, runs as an unprivileged user (SECURITY.md §1).
FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S -g 10001 inspecto && adduser -S -u 10001 -G inspecto -h /app inspecto
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/app.jar app.jar
USER 10001:10001
EXPOSE 8081
# The JVM honours the container memory limit; the flag keeps heap at a share of it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8081/health/live || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
