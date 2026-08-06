#!/bin/sh

set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "Usage: $0 <staged-context-root> [image-tag]" >&2
    exit 2
fi

context_root=$1
properties_file=$context_root/image-build.properties
snapshot_context=$context_root/snapshot

if [ ! -f "$properties_file" ] || [ ! -d "$snapshot_context" ]; then
    echo "The staged Docker context is incomplete. Run stageFeatureModelDockerContext first." >&2
    exit 1
fi

. "$properties_file"

: "${ARTEMIS_COMMIT:?missing ARTEMIS_COMMIT}"
: "${EXTRACTOR_VERSION:?missing EXTRACTOR_VERSION}"
: "${FEATURE_MODEL_REPOSITORY_COMMIT:?missing FEATURE_MODEL_REPOSITORY_COMMIT}"
: "${MANIFEST_DIGEST:?missing MANIFEST_DIGEST}"
: "${SNAPSHOT_DIGEST:?missing SNAPSHOT_DIGEST}"
: "${SNAPSHOT_ID:?missing SNAPSHOT_ID}"

image_tag=${2:-artemis-feature-model:snapshot-$SNAPSHOT_ID}
script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(dirname "$script_directory")

docker build \
    --build-context "feature_model_snapshot=$snapshot_context" \
    --build-arg "ARTEMIS_COMMIT=$ARTEMIS_COMMIT" \
    --build-arg "EXTRACTOR_VERSION=$EXTRACTOR_VERSION" \
    --build-arg "FEATURE_MODEL_REPOSITORY_COMMIT=$FEATURE_MODEL_REPOSITORY_COMMIT" \
    --build-arg "MANIFEST_DIGEST=$MANIFEST_DIGEST" \
    --build-arg "SNAPSHOT_DIGEST=$SNAPSHOT_DIGEST" \
    --build-arg "SNAPSHOT_ID=$SNAPSHOT_ID" \
    --tag "$image_tag" \
    "$repository_root"

echo "image=$image_tag"
