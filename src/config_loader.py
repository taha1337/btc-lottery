"""Loads and validates the JSON checker config."""

import json
from pathlib import Path
from typing import Any, Dict


REQUIRED_HEADER_FIELDS = (
    "version",
    "previous_block_hash",
    "merkle_root",
    "timestamp",
    "bits",
)


class ConfigError(ValueError):
    pass


def load_config(path: str) -> Dict[str, Any]:
    config_path = Path(path)
    if not config_path.exists():
        raise ConfigError(f"Config file not found: {path}")

    with config_path.open("r", encoding="utf-8") as f:
        try:
            config = json.load(f)
        except json.JSONDecodeError as exc:
            raise ConfigError(f"Config file is not valid JSON: {exc}") from exc

    _validate(config)
    return config


def _validate(config: Dict[str, Any]) -> None:
    if not isinstance(config, dict):
        raise ConfigError("Config must be a JSON object")
    if config.get("worker_url"):
        from urllib.parse import urlparse

        if not isinstance(config["worker_url"], str):
            raise ConfigError("worker_url must be an HTTP(S) URL")
        try:
            url = urlparse(config["worker_url"])
        except ValueError as exc:
            raise ConfigError("worker_url must be a valid HTTP(S) URL") from exc
        if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password:
            raise ConfigError("worker_url must be an HTTP(S) URL without credentials")
        if url.scheme == "http" and url.hostname not in ("localhost", "127.0.0.1", "::1"):
            raise ConfigError("Remote Workers require HTTPS")
        if not isinstance(config.get("payout_address"), str) or not config["payout_address"].strip():
            raise ConfigError("Set payout_address to your Bitcoin receiving address")
    elif "block_header_template" not in config:
        raise ConfigError("Config is missing 'block_header_template'")

    if not config.get("worker_url"):
        header = config["block_header_template"]
        if not isinstance(header, dict):
            raise ConfigError("block_header_template must be a JSON object")
        missing = [field for field in REQUIRED_HEADER_FIELDS if field not in header]
        if missing:
            raise ConfigError(f"block_header_template is missing fields: {missing}")
        for field in ("previous_block_hash", "merkle_root"):
            if not isinstance(header[field], str) or len(header[field]) != 64:
                raise ConfigError(f"{field} must be a 64-char hex string")
            try:
                bytes.fromhex(header[field])
            except ValueError as exc:
                raise ConfigError(f"{field} must be hexadecimal") from exc

    if "nonces_to_try" not in config or not isinstance(config["nonces_to_try"], list):
        raise ConfigError("Config must contain a 'nonces_to_try' list")
    if not config["nonces_to_try"]:
        raise ConfigError("'nonces_to_try' must contain at least one nonce")

    for nonce in config["nonces_to_try"]:
        if type(nonce) is not int or not (0 <= nonce <= 0xFFFFFFFF):
            raise ConfigError(f"Invalid nonce (must be a 32-bit unsigned int): {nonce}")
