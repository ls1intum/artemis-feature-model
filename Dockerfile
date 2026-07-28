FROM node:24-bookworm-slim AS frontend-build

WORKDIR /workspace

COPY package.json package-lock.json angular.json tsconfig.json tsconfig.app.json ./
COPY src/main/webapp ./src/main/webapp

RUN npm ci
RUN npm run build:prod

FROM eclipse-temurin:25-jdk AS backend-build

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources
COPY --from=frontend-build /workspace/build/webapp ./build/webapp

RUN ./gradlew --no-daemon bootJar -PskipFrontendBuild=true

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=70 -XX:+UseSerialGC" \
    SERVER_PORT=8080 \
    ARTEMIS_FEATURE_MODEL_DATA_ROOT=/app/data

RUN mkdir -p /app/data && chown -R 10001:10001 /app

COPY --chown=10001:10001 --from=backend-build /workspace/build/libs/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
