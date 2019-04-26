#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

cd $DIR/..

# Bundle Kafka Lag Exporter Helm Chart into a tarball artifact.  The `helm package` command will output the artifact
# in the CWD it is executed from.
echo Bundle helm chart
helm package ./charts/kafka-lag-exporter