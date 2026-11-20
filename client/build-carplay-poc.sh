#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# Local source generation only; signing, installation and Apple account changes are separate.
"$client_dir/build-ios-trial.sh"
python3 "$client_dir/prepare-carplay-project.py" \
    "$client_dir/common/target/client-common-0.1.0-SNAPSHOT-ios-source"
