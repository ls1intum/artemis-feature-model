#!/bin/sh

set -eu

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <image> <staged-context-root>" >&2
    exit 2
fi

image=$1
context_root=$2
properties_file=$context_root/image-build.properties

if [ ! -f "$properties_file" ]; then
    echo "Missing staged image-build.properties." >&2
    exit 1
fi

. "$properties_file"

: "${ARTEMIS_COMMIT:?missing ARTEMIS_COMMIT}"
: "${EXTRACTOR_VERSION:?missing EXTRACTOR_VERSION}"
: "${FEATURE_MODEL_REPOSITORY_COMMIT:?missing FEATURE_MODEL_REPOSITORY_COMMIT}"
: "${MANIFEST_DIGEST:?missing MANIFEST_DIGEST}"
: "${SNAPSHOT_DIGEST:?missing SNAPSHOT_DIGEST}"
: "${SNAPSHOT_ID:?missing SNAPSHOT_ID}"

container_id=
temporary_directory=$(mktemp -d)
tamper_image="artemis-feature-model:tamper-test-$$"
missing_image="artemis-feature-model:missing-test-$$"

cleanup() {
    if [ -n "$container_id" ]; then
        docker rm -f "$container_id" >/dev/null 2>&1 || true
    fi
    docker image rm -f "$tamper_image" "$missing_image" >/dev/null 2>&1 || true
    rm -rf "$temporary_directory"
}
trap cleanup EXIT INT TERM

assert_label() {
    label_name=$1
    expected=$2
    actual=$(docker image inspect "$image" --format "{{ index .Config.Labels \"$label_name\" }}")
    test "$actual" = "$expected"
}

assert_label org.opencontainers.image.revision "$FEATURE_MODEL_REPOSITORY_COMMIT"
assert_label de.tum.cit.aet.artemis-feature-model.artemis-commit "$ARTEMIS_COMMIT"
assert_label de.tum.cit.aet.artemis-feature-model.manifest-digest "$MANIFEST_DIGEST"
assert_label de.tum.cit.aet.artemis-feature-model.snapshot-id "$SNAPSHOT_ID"
assert_label de.tum.cit.aet.artemis-feature-model.snapshot-digest "$SNAPSHOT_DIGEST"
assert_label de.tum.cit.aet.artemis-feature-model.extractor-version "$EXTRACTOR_VERSION"

test "$(docker run --rm --entrypoint id "$image" -u)" = "10001"

container_id=$(docker run -d --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m -p 127.0.0.1::8080 "$image")
host_port=$(docker port "$container_id" 8080/tcp | sed 's/.*://')
attempt=0
while [ "$attempt" -lt 60 ]; do
    if curl --fail --silent "http://127.0.0.1:$host_port/api/feature-model/provenance" >"$temporary_directory/provenance.json"; then
        break
    fi
    if [ "$(docker inspect "$container_id" --format '{{.State.Running}}')" != "true" ]; then
        docker logs "$container_id" >&2
        exit 1
    fi
    attempt=$((attempt + 1))
    sleep 1
done
test "$attempt" -lt 60

jq -e --arg value "$SNAPSHOT_ID" '.sourceMode == "snapshot" and .snapshotId == $value' "$temporary_directory/provenance.json" >/dev/null
jq -e --arg value "$SNAPSHOT_DIGEST" '.snapshotDigest == $value' "$temporary_directory/provenance.json" >/dev/null
jq -e --arg value "$ARTEMIS_COMMIT" '.artemisCommit == $value' "$temporary_directory/provenance.json" >/dev/null
jq -e --arg value "$MANIFEST_DIGEST" '.manifestDigest == $value' "$temporary_directory/provenance.json" >/dev/null
jq -e --arg value "$EXTRACTOR_VERSION" '.extractorVersion == $value' "$temporary_directory/provenance.json" >/dev/null

test "$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$host_port/api/feature-model/snapshots")" = "404"
test "$(docker exec "$container_id" find "/opt/artemis-feature-model/data/imported-models/$SNAPSHOT_ID" -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 7
test "$(docker inspect "$container_id" --format '{{ range .Mounts }}{{ if eq .Destination "/opt/artemis-feature-model/data" }}mounted{{ end }}{{ end }}')" = ""
docker rm -f "$container_id" >/dev/null
container_id=

printf '%s\n' \
    'ARG BASE_IMAGE' \
    'FROM ${BASE_IMAGE}' \
    'ARG SNAPSHOT_ID' \
    'ARG MUTATION' \
    'USER 0' \
    'RUN if [ "$MUTATION" = "missing" ]; then rm "/opt/artemis-feature-model/data/imported-models/$SNAPSHOT_ID/feature-model.json"; else chmod 0644 "/opt/artemis-feature-model/data/imported-models/$SNAPSHOT_ID/feature-model.json" && printf "corrupt\\n" > "/opt/artemis-feature-model/data/imported-models/$SNAPSHOT_ID/feature-model.json"; fi' \
    'USER 10001:10001' >"$temporary_directory/Dockerfile"

verify_failed_start() {
    mutation=$1
    derived_image=$2
    docker build --quiet --build-arg "BASE_IMAGE=$image" --build-arg "SNAPSHOT_ID=$SNAPSHOT_ID" --build-arg "MUTATION=$mutation" \
        --tag "$derived_image" "$temporary_directory" >/dev/null
    failed_container=$(docker run -d "$derived_image")
    running=true
    count=0
    while [ "$count" -lt 30 ]; do
        running=$(docker inspect "$failed_container" --format '{{.State.Running}}')
        if [ "$running" != "true" ]; then
            break
        fi
        count=$((count + 1))
        sleep 1
    done
    if [ "$running" = "true" ]; then
        docker rm -f "$failed_container" >/dev/null
        echo "$mutation snapshot unexpectedly started." >&2
        exit 1
    fi
    test "$(docker inspect "$failed_container" --format '{{.State.ExitCode}}')" -ne 0
    docker rm "$failed_container" >/dev/null
}

verify_failed_start tampered "$tamper_image"
verify_failed_start missing "$missing_image"

echo "snapshot image verification passed for $image"
