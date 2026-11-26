#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
origin=${NORMALBIAS_API_ORIGIN:-http://127.0.0.1:8080}
# This unauthenticated development client connects to a local API only.
if [[ ! "$origin" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?/?$ ]]; then
    printf 'NORMALBIAS_API_ORIGIN must be a loopback HTTP origin, e.g. http://127.0.0.1:8090\n' >&2
    exit 2
fi
# cn1:run forks Maven and then Java. JAVA_TOOL_OPTIONS reaches the simulator JVM.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dnormalbias.apiOrigin=$origin -Dnormalbias.carRowActions=true"
# FFmpeg inherits environment proxies independently of the simulator's Java proxy setting.
export no_proxy="${no_proxy:-},127.0.0.1,localhost,::1"
export NO_PROXY="${NO_PROXY:-},127.0.0.1,localhost,::1"
exec mvn -f "$client_dir/pom.xml" -B -ntp -pl common cn1:run "$@"
