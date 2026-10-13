#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

if [ -d "venv" ]; then
    # shellcheck disable=SC1091
    source venv/bin/activate
fi

python3 main.py --config config/miner_config.json "$@"
