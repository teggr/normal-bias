#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
origin=${NORMALBIAS_API_ORIGIN:-http://127.0.0.1:18080}
port=${NORMALBIAS_CARPLAY_MCP_PORT:-18866}
if [[ ! "$origin" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?/?$ ]]; then
    printf 'The desktop probe requires a loopback HTTP origin.\n' >&2; exit 2
fi
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then
    printf 'Invalid NORMALBIAS_CARPLAY_MCP_PORT.\n' >&2; exit 2
fi
if [[ $(java -version 2>&1) != *'version "21.'* ]]; then
    printf 'Select Java 21 before running the probe.\n' >&2; exit 2
fi
python3 - "$client_dir" <<'PY'
import shlex
import sys
from pathlib import Path
client = Path(sys.argv[1])
args_path = client / 'common/target/codenameone/simulator-classpath.args'
if not args_path.exists():
    raise SystemExit('Start ./client/demo-simulator.sh first to prepare the pinned simulator classpath and synthetic API.')
args = shlex.split(args_path.read_text())
if len(args) != 2 or args[0] != '-classpath':
    raise SystemExit('Unexpected simulator argument file; review the pinned toolchain.')
target = client / 'target/carplay-probe'
classes = target / 'classes'
classes.mkdir(parents=True, exist_ok=True)
(target / 'compile.args').write_text('-classpath\n"' + args[1] + '"\n-d\n"' + str(classes)
                                     + '"\n"' + str(client / 'tests/CarPlayProbe.java') + '"\n')
(target / 'run.args').write_text('-classpath\n"' + str(classes) + ':' + args[1] + '"\n')
PY
javac @"$client_dir/target/carplay-probe/compile.args"
export no_proxy="${no_proxy:-},127.0.0.1,localhost,::1"
export NO_PROXY="${NO_PROXY:-},127.0.0.1,localhost,::1"
exec java -Dcn1.mcp.port="$port" -Dnormalbias.apiOrigin="$origin" \
    -Dnormalbias.carProbeOutput="$client_dir/target/recordings/carplay" \
    @"$client_dir/target/carplay-probe/run.args" \
    com.codename1.impl.javase.Simulator dev.normalbias.client.CarPlayProbe
