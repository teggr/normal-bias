#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname -- "$client_dir")
port=${NORMALBIAS_DEMO_PORT:-18080}
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then
    printf 'NORMALBIAS_DEMO_PORT must be between 1024 and 65535\n' >&2
    exit 2
fi
for program in java mvn ffmpeg ffprobe curl; do
    command -v "$program" >/dev/null || { printf 'Required program missing: %s\n' "$program" >&2; exit 2; }
done
mvn -f "$repo_dir/pom.xml" -B -ntp verify
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/normal-bias-simulator.XXXXXX")
api_pid=
cleanup() {
    if [[ -n "$api_pid" ]]; then
        kill "$api_pid" 2>/dev/null || true
        wait "$api_pid" 2>/dev/null || true
    fi
    rm -rf -- "$work_dir"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$work_dir/music"
ffmpeg -v error -f lavfi -i 'aevalsrc=0.12*sin(2*PI*220*t)+0.07*sin(2*PI*330*t)+0.05*sin(2*PI*440*t):s=44100:d=42' \
    -af 'afade=t=in:d=0.5,afade=t=out:st=40:d=2' -metadata title='Evening Signal' \
    -metadata artist='Normal Bias Demo' -metadata album='Simulator Sessions' "$work_dir/music/evening.mp3"
ffmpeg -v error -f lavfi -i 'aevalsrc=0.12*sin(2*PI*261.63*t)+0.07*sin(2*PI*392*t)+0.05*sin(2*PI*523.25*t):s=44100:d=42' \
    -af 'afade=t=in:d=0.5,afade=t=out:st=40:d=2' -metadata title='Morning Circuit' \
    -metadata artist='Normal Bias Demo' -metadata album='Simulator Sessions' "$work_dir/music/morning.mp3"
origin="http://127.0.0.1:$port"
java -jar "$repo_dir/api/target/normal-bias-api-0.1.0-SNAPSHOT.jar" \
    --server.address=127.0.0.1 --server.port="$port" --normalbias.data-dir="$work_dir/data" > "$work_dir/api.log" 2>&1 &
api_pid=$!
ready=false
for ((attempt=0; attempt<60; attempt++)); do
    if ! kill -0 "$api_pid" 2>/dev/null; then cat "$work_dir/api.log" >&2; exit 1; fi
    # Require our own startup log before probing: never upload to a pre-existing server.
    if [[ $(<"$work_dir/api.log") == *"Started NormalBiasApplication"* ]] && \
            curl --noproxy '*' -fsS --max-time 1 "$origin/api/tracks" > /dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 0.5
done
if [[ "$ready" != true ]]; then cat "$work_dir/api.log" >&2; exit 1; fi
java -jar "$repo_dir/cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar" "$work_dir/music" "$origin"
printf 'Starting simulator with a disposable synthetic library at %s\n' "$origin"
NORMALBIAS_API_ORIGIN="$origin" "$client_dir/run-simulator.sh" "$@"
