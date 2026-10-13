"""Simple logging configuration shared by the whole project."""

import logging
import sys


def setup_logger(name: str = "btc-nonce-checker", level: str = "INFO") -> logging.Logger:
    logger = logging.getLogger(name)

    if logger.handlers:
        
        return logger

    logger.setLevel(getattr(logging, level.upper(), logging.INFO))

    handler = logging.StreamHandler(sys.stdout)
    formatter = logging.Formatter(
        fmt="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    handler.setFormatter(formatter)
    logger.addHandler(handler)

    return logger
