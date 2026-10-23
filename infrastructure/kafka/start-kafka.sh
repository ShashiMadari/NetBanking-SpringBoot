#!/bin/bash
set -euo pipefail
export LOG_DIR=/tmp/kafka-logs
config=/opt/kafka/config/server.properties
if [ ! -f /var/lib/kafka/data/meta.properties ]; then
  cluster_id="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
  /opt/kafka/bin/kafka-storage.sh format --standalone -t "$cluster_id" -c "$config"
fi
exec /opt/kafka/bin/kafka-server-start.sh "$config"
