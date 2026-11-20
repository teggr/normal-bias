#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname -- "$client_dir")
source "$client_dir/trial-env.sh"
trial_origin
for program in java ffmpeg curl; do command -v "$program" >/dev/null || { printf 'Missing %s\n' "$program" >&2; exit 2; }; done
for module in api cli; do test -f "$repo_dir/$module/target/normal-bias-$module-0.1.0-SNAPSHOT.jar" || { printf 'Run mvn clean verify first\n' >&2; exit 2; }; done
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/normal-bias-device.XXXXXX")
api_pid=
cleanup() {
    if [[ -n "$api_pid" ]]; then kill "$api_pid" 2>/dev/null || true; wait "$api_pid" 2>/dev/null || true; fi
    rm -rf -- "$work_dir"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$work_dir/music"
ffmpeg -v error -f lavfi -i 'aevalsrc=0.12*sin(2*PI*220*t)+0.07*sin(2*PI*330*t)+0.05*sin(2*PI*440*t):s=44100:d=42' \
    -af 'afade=t=in:d=0.5,afade=t=out:st=40:d=2' -metadata title='Evening Signal' \
    -metadata artist='Normal Bias Demo' -metadata album='Device Trial' "$work_dir/music/evening.mp3"
ffmpeg -v error -f lavfi -i 'aevalsrc=0.12*sin(2*PI*261.63*t)+0.07*sin(2*PI*392*t)+0.05*sin(2*PI*523.25*t):s=44100:d=42' \
    -af 'afade=t=in:d=0.5,afade=t=out:st=40:d=2' -metadata title='Morning Circuit' \
    -metadata artist='Normal Bias Demo' -metadata album='Device Trial' "$work_dir/music/morning.mp3"
start_api() {
    local host=$1 ready=false
    : > "$work_dir/api.log"
    java -jar "$repo_dir/api/target/normal-bias-api-0.1.0-SNAPSHOT.jar" \
        --server.address="$host" --server.port="$trial_port" --normalbias.data-dir="$work_dir/data" > "$work_dir/api.log" 2>&1 &
    api_pid=$!
    for ((attempt=0; attempt<60; attempt++)); do
        if ! kill -0 "$api_pid" 2>/dev/null; then cat "$work_dir/api.log" >&2; return 1; fi
        if [[ $(<"$work_dir/api.log") == *'Started NormalBiasApplication'* ]] && \
                curl --noproxy '*' -fsS --max-time 1 "http://$host:$trial_port/api/tracks" > /dev/null 2>&1; then ready=true; break; fi
        sleep 0.5
    done
    if [[ $ready != true ]]; then cat "$work_dir/api.log" >&2; return 1; fi
}
# Upload through loopback, then restart the same disposable data on the selected private interface.
start_api 127.0.0.1
java -jar "$repo_dir/cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar" "$work_dir/music" "http://127.0.0.1:$trial_port"
kill "$api_pid"; wait "$api_pid" || true; api_pid=
start_api "$trial_host"
printf 'Disposable API ready: %s\nLogs: %s/api.log\nHelper PID: %s\nCtrl-C or kill -TERM %s stops it and removes its synthetic library.\n' "$NORMALBIAS_API_ORIGIN" "$work_dir" "$$" "$$"
wait "$api_pid"
