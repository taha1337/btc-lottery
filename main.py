#!/usr/bin/env python3
"""CLI entry point for btc-nonce-checker."""

import argparse
import sys

from src.config_loader import ConfigError, load_config
from src.checker import run_checker
from src.worker_client import WorkerError


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Try a list of nonces against a Bitcoin block header template."
    )
    parser.add_argument(
        "--config",
        default="config/checker_config.json",
        help="Path to the JSON config file (default: config/checker_config.json)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        config = load_config(args.config)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1

    try:
        run_checker(config)
    except (WorkerError, ValueError) as exc:
        print(f"Checking error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
