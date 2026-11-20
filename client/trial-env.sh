#!/usr/bin/env bash
# Shared validation for the disposable, private IPv4 iPhone trial.
trial_origin() {
    local value=${NORMALBIAS_API_ORIGIN:-} a b c d port
    if [[ ! "$value" =~ ^http://([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3}):([0-9]{1,5})$ ]]; then
        printf 'Set NORMALBIAS_API_ORIGIN to http://PRIVATE_IPV4:PORT (1024–65535), without a trailing slash\n' >&2
        return 2
    fi
    a=${BASH_REMATCH[1]} b=${BASH_REMATCH[2]} c=${BASH_REMATCH[3]} d=${BASH_REMATCH[4]} port=${BASH_REMATCH[5]}
    local octet
    for octet in "$a" "$b" "$c" "$d"; do
        if [[ ${#octet} -gt 1 && $octet == 0* ]] || (( 10#$octet > 255 )); then
            printf 'Use an unambiguous private IPv4 address\n' >&2; return 2
        fi
    done
    if ! (( 10#$a == 10 || (10#$a == 172 && 10#$b >= 16 && 10#$b <= 31) || (10#$a == 192 && 10#$b == 168) )); then
        printf 'The trial requires an RFC1918 private IPv4 address\n' >&2; return 2
    fi
    if (( 10#$port < 1024 || 10#$port > 65535 )); then
        printf 'Trial port must be 1024–65535\n' >&2; return 2
    fi
    trial_host="$a.$b.$c.$d"
    trial_port=$((10#$port))
}
