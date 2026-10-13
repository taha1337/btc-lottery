"""Builds and serializes an 80-byte Bitcoin block header."""

import struct
from dataclasses import dataclass


@dataclass
class BlockHeader:
    version: int
    previous_block_hash: str  
    merkle_root: str  
    timestamp: int
    bits: int
    nonce: int = 0

    def serialize(self) -> bytes:
        prev_hash_le = bytes.fromhex(self.previous_block_hash)[::-1]
        merkle_root_le = bytes.fromhex(self.merkle_root)[::-1]

        return (
            struct.pack("<I", self.version)
            + prev_hash_le
            + merkle_root_le
            + struct.pack("<I", self.timestamp)
            + struct.pack("<I", self.bits)
            + struct.pack("<I", self.nonce)
        )

    def with_nonce(self, nonce: int) -> "BlockHeader":
        return BlockHeader(
            version=self.version,
            previous_block_hash=self.previous_block_hash,
            merkle_root=self.merkle_root,
            timestamp=self.timestamp,
            bits=self.bits,
            nonce=nonce,
        )

    @classmethod
    def from_config(cls, header_config: dict) -> "BlockHeader":
        from .target import bits_hex_to_int

        bits = header_config["bits"]
        bits_int = bits_hex_to_int(bits) if isinstance(bits, str) else int(bits)

        return cls(
            version=int(header_config["version"]),
            previous_block_hash=header_config["previous_block_hash"],
            merkle_root=header_config["merkle_root"],
            timestamp=int(header_config["timestamp"]),
            bits=bits_int,
            nonce=0,
        )
