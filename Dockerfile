ARG RUNTIME_BASE_IMAGE=eclipse-temurin:25-jre
FROM ${RUNTIME_BASE_IMAGE} AS runtime

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

COPY --from=application_jar --chown=10001:10001 artemis-feature-model-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=feature_model_snapshot --chown=10001:10001 --chmod=0444 . /opt/artemis-feature-model/data/imported-models/${SNAPSHOT_ID}/

RUN test "$(find /opt/artemis-feature-model/data/imported-models -mindepth 1 -maxdepth 1 -type d | wc -l)" -eq 1 \
    && test "$(find /opt/artemis-feature-model/data/imported-models/${SNAPSHOT_ID} -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 7 \
    && find /opt/artemis-feature-model -type d -exec chmod 0555 {} + \
    && find /opt/artemis-feature-model -type f -exec chmod 0444 {} +

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
