"""Double-SHA256 hashing helpers used throughout the Bitcoin protocol."""

import hashlib


def double_sha256(data: bytes) -> bytes:
    return hashlib.sha256(hashlib.sha256(data).digest()).digest()


def to_display_hex(hash_bytes: bytes) -> str:
    return hash_bytes[::-1].hex()


def hash_to_int(hash_bytes: bytes) -> int:
    return int.from_bytes(hash_bytes, byteorder="little")
