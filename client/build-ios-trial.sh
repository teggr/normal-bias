#!/usr/bin/env bash
set -euo pipefail
client_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$client_dir/trial-env.sh"
trial_origin
# This helper deliberately has no arbitrary Maven arguments or cloud-target override.
if [[ $(java -version 2>&1) != *'version "21.'* ]]; then
    printf 'Select the Java 21 JDK before building\n' >&2; exit 2
fi
# Do not let a subsequent ordinary browser/simulator package retain the trial origin.
trap 'rm -f -- "$client_dir/common/target/classes/normalbias-trial-origin.txt"' EXIT
plist="<key>NSAppTransportSecurity</key><dict><key>NSAllowsArbitraryLoads</key><false/><key>NSExceptionDomains</key><dict><key>$trial_host</key><dict><key>NSExceptionAllowsInsecureHTTPLoads</key><true/></dict></dict></dict><key>NSLocalNetworkUsageDescription</key><string>Browse and play the disposable Normal Bias library on your private development computer.</string>"
if [[ "$NORMALBIAS_API_ORIGIN" == https://* ]]; then
    plist="<key>NSLocalNetworkUsageDescription</key><string>Browse and play the private Normal Bias library over HTTPS.</string>"
fi
mvn -f "$client_dir/pom.xml" -B -ntp package -Pnative-trial \
    -Dcodename1.platform=ios -Dcodename1.buildTarget=ios-source -Dopen=false \
    -Dnormalbias.trial.origin="$NORMALBIAS_API_ORIGIN" \
    -Dcodename1.arg.ios.deployment_target=17.0 \
    -Dcodename1.arg.ios.plistInject="$plist"
project="$client_dir/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj"
test -f "$project/project.pbxproj" || { printf 'No generated Xcode project\n' >&2; exit 1; }
printf 'Local trial Xcode project: %s\nAPI origin: %s\nSelect your signing team in Xcode or pass DEVELOPMENT_TEAM to xcodebuild.\n' "$project" "$NORMALBIAS_API_ORIGIN"
