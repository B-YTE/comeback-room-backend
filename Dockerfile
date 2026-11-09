FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN bash gradlew dependencies --no-daemon

COPY src ./src
RUN bash gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system comebackroom \
    && useradd --system --gid comebackroom --home-dir /app comebackroom

WORKDIR /app

COPY --from=builder --chown=comebackroom:comebackroom /workspace/build/libs/app.jar ./app.jar

USER comebackroom

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
