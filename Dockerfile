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

ARG SNAPSHOT_ID
ARG SNAPSHOT_DIGEST
ARG ARTEMIS_COMMIT
ARG MANIFEST_DIGEST
ARG FEATURE_MODEL_REPOSITORY_COMMIT
ARG EXTRACTOR_VERSION

LABEL org.opencontainers.image.revision="${FEATURE_MODEL_REPOSITORY_COMMIT}" \
      de.tum.cit.aet.artemis-feature-model.artemis-commit="${ARTEMIS_COMMIT}" \
      de.tum.cit.aet.artemis-feature-model.manifest-digest="${MANIFEST_DIGEST}" \
      de.tum.cit.aet.artemis-feature-model.snapshot-id="${SNAPSHOT_ID}" \
      de.tum.cit.aet.artemis-feature-model.snapshot-digest="${SNAPSHOT_DIGEST}" \
      de.tum.cit.aet.artemis-feature-model.extractor-version="${EXTRACTOR_VERSION}"

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=70 -XX:+UseSerialGC" \
    SERVER_PORT=8080 \
    ARTEMIS_FEATURE_MODEL_SOURCE_MODE=snapshot \
    ARTEMIS_FEATURE_MODEL_DATA_ROOT=/opt/artemis-feature-model/data \
    ARTEMIS_FEATURE_MODEL_ACTIVE_SNAPSHOT_ID=${SNAPSHOT_ID} \
    ARTEMIS_FEATURE_MODEL_SNAPSHOT_ADMIN_API_ENABLED=false

RUN test -n "${SNAPSHOT_ID}" \
    && test -n "${SNAPSHOT_DIGEST}" \
    && test -n "${ARTEMIS_COMMIT}" \
    && test -n "${MANIFEST_DIGEST}" \
    && test -n "${FEATURE_MODEL_REPOSITORY_COMMIT}" \
    && test -n "${EXTRACTOR_VERSION}" \
    && mkdir -p /opt/artemis-feature-model/data/imported-models \
    && chown -R 10001:10001 /app /opt/artemis-feature-model

COPY --chown=10001:10001 --from=backend-build /workspace/build/libs/*.jar /app/app.jar
COPY --from=feature_model_snapshot --chown=10001:10001 --chmod=0444 . /opt/artemis-feature-model/data/imported-models/${SNAPSHOT_ID}/

RUN test "$(find /opt/artemis-feature-model/data/imported-models -mindepth 1 -maxdepth 1 -type d | wc -l)" -eq 1 \
    && test "$(find /opt/artemis-feature-model/data/imported-models/${SNAPSHOT_ID} -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 7 \
    && find /opt/artemis-feature-model -type d -exec chmod 0555 {} + \
    && find /opt/artemis-feature-model -type f -exec chmod 0444 {} +

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
