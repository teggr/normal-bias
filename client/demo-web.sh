#!/usr/bin/env bash
# Disposable browser demo. Never connects to an existing library.
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname -- "$client_dir")
port=${NORMALBIAS_DEMO_PORT:-18080}
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then
    printf 'NORMALBIAS_DEMO_PORT must be between 1024 and 65535\n' >&2
    exit 2
fi
port=$((10#$port))
music_dir=${NORMALBIAS_DEMO_MUSIC_DIR:-}
if [[ -n "$music_dir" && ( ! -d "$music_dir" || -L "$music_dir" ) ]]; then
    printf 'NORMALBIAS_DEMO_MUSIC_DIR must be an existing directory, not a symlink.\n' >&2
    exit 2
fi
for program in java mvn python3 unzip curl ffmpeg ffprobe; do
    command -v "$program" >/dev/null || { printf 'Required program missing: %s\n' "$program" >&2; exit 2; }
done
if [[ -n "$music_dir" ]]; then
    python3 - "$music_dir" <<'PY'
import os, sys
for root, dirs, files in os.walk(sys.argv[1], followlinks=False):
    if any(name.lower().endswith('.mp3') and not os.path.islink(os.path.join(root, name))
           and os.path.isfile(os.path.join(root, name)) for name in files):
        break
else:
    raise SystemExit('The selected demo music directory contains no regular MP3 files.')
PY
fi
if [[ $(java -version 2>&1) != *'version "21.'* ]]; then
    printf 'Select a Java 21 JDK (JAVA_HOME and PATH) before running the demo.\n' >&2
    exit 2
fi
maven_info=$(mvn -version 2>&1)
python3 - "$maven_info" <<'PY'
import re, sys
version = re.search(r'Apache Maven (\d+)\.(\d+)\.(\d+)', sys.argv[1])
if not version or tuple(map(int, version.groups())) < (3, 6, 3):
    raise SystemExit('Maven 3.6.3 or newer is required.')
PY
if [[ "$maven_info" != *'Java version: 21.'* ]]; then
    printf 'Maven must also use Java 21. Check mvn -version and JAVA_HOME.\n' >&2
    exit 2
fi
python3 - "$port" <<'PY'
import socket, sys
with socket.socket() as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(('127.0.0.1', int(sys.argv[1])))
    except OSError as error:
        raise SystemExit(f'Demo port {sys.argv[1]} unavailable: {error}. Set NORMALBIAS_DEMO_PORT to another port.')
PY
printf 'Building the API and browser client. The first run downloads Maven/Codename One tools.\n'
mvn -f "$repo_dir/pom.xml" -B -ntp verify
"$client_dir/build-web.sh"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/normal-bias-web.XXXXXX")
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
if [[ -z "$music_dir" ]]; then
    music_dir="$work_dir/music"
    mkdir -p "$music_dir"
    for name in evening morning; do
        if [[ "$name" == evening ]]; then
            title='Evening Signal'; frequency=220; colour=0x4e90b5
        else
            title='Morning Circuit'; frequency=261.63; colour=0xeac23f
        fi
        ffmpeg -v error -f lavfi -i "color=c=0x202124:s=512x512:d=0.04,drawbox=x=48:y=48:w=416:h=416:color=$colour:t=12,drawbox=x=112:y=112:w=288:h=288:color=$colour:t=fill,drawbox=x=176:y=176:w=160:h=160:color=0x202124:t=fill" \
            -frames:v 1 "$work_dir/$name.png"
        ffmpeg -v error -f lavfi -i "aevalsrc=0.12*sin(2*PI*$frequency*t)+0.07*sin(2*PI*($frequency*1.5)*t)+0.05*sin(2*PI*($frequency*2)*t):s=44100:d=60" \
            -i "$work_dir/$name.png" -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v copy \
            -af 'afade=t=in:d=0.5,afade=t=out:st=58:d=2' -id3v2_version 3 -disposition:v attached_pic \
            -metadata title="$title" -metadata artist='Normal Bias Demo' -metadata album='Simulator Sessions' "$work_dir/music/$name.mp3"
    done
fi
origin="http://127.0.0.1:$port"
java -jar "$repo_dir/api/target/normal-bias-api-0.1.0-SNAPSHOT.jar" \
    --server.address=127.0.0.1 --server.port="$port" --normalbias.auth.mode=demo \
    --normalbias.data-dir="$work_dir/data" --normalbias.artwork.background-enabled=false \
    --spring.web.resources.static-locations="file:$client_dir/target/web/" > "$work_dir/api.log" 2>&1 &
api_pid=$!
ready=false
for ((attempt=0; attempt<120; attempt++)); do
    if ! kill -0 "$api_pid" 2>/dev/null; then cat "$work_dir/api.log" >&2; exit 1; fi
    # A racing listener must never receive the demo uploads.
    if [[ $(<"$work_dir/api.log") == *"Started NormalBiasApplication"* ]] && \
            curl --noproxy '*' -fsS --max-time 1 "$origin/api/tracks" > /dev/null 2>&1; then
        ready=true; break
    fi
    sleep 0.5
done
if [[ "$ready" != true ]]; then cat "$work_dir/api.log" >&2; exit 1; fi
java -jar "$repo_dir/cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar" "$music_dir" "$origin"
printf '\nNormal Bias is ready: %s/\nNo sign-in needed.\nTemporary library: %s\nKeep this terminal open. Ctrl-C stops the demo and deletes its imported copies; source music is preserved.\n' "$origin" "$work_dir"
wait "$api_pid"
