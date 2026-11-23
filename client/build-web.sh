#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
mvn -f "$client_dir/pom.xml" -B -ntp package \
    -Dcodename1.platform=javascript -Dcodename1.buildTarget=local-javascript "$@"
bundle="$client_dir/common/target/client-common-0.1.0-SNAPSHOT.zip"
# Replace only this generated output, so old worker/assets cannot survive a rebuild.
rm -rf -- "$client_dir/target/web"
mkdir -p "$client_dir/target/web"
unzip -q "$bundle" -d "$client_dir/target/web"
# Capture one-time fragments before framework startup and remove them from history.
# Tokens stay in transient page memory until Java consumes the link.
cp "$client_dir/account-link.js" "$client_dir/target/web/account-link.js"
python3 "$client_dir/prepare-web.py" "$client_dir/target/web"
printf 'Web client ready in %s/target/web\n' "$client_dir"
