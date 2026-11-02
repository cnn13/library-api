# syntax=docker/dockerfile:1

# ---------- stage 1: зависимости (кэшируются, пока не меняется pom.xml) ----------
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# ---------- stage 2: тесты и сборка jar ----------
FROM deps AS build
COPY src ./src
# тесты выполняются при каждой сборке образа: упавший тест = образ не собирается
RUN mvn -B package \
 && java -Djarmode=tools -jar target/library-api.jar extract --layers --launcher --destination target/extracted

# ---------- stage 3: финальный образ — только JRE и приложение ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
# слои Spring Boot: редко меняющиеся зависимости отдельно от кода приложения
COPY --from=build /src/target/extracted/dependencies/ ./
COPY --from=build /src/target/extracted/spring-boot-loader/ ./
COPY --from=build /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build /src/target/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
