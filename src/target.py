"""Decodes Bitcoin's target and checks hashes against it."""


def bits_to_target(bits: int) -> int:
    exponent = bits >> 24
    mantissa = bits & 0x007FFFFF if (bits & 0x00800000) else bits & 0x00FFFFFF

    if exponent <= 3:
        target = mantissa >> (8 * (3 - exponent))
    else:
        target = mantissa << (8 * (exponent - 3))

    return target


def bits_hex_to_int(bits_hex: str) -> int:
    return int(bits_hex, 16)


def hash_meets_target(hash_int: int, target: int) -> bool:
    return hash_int <= target
