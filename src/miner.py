"""Fetch a live candidate, check nonce guesses, and submit a winner."""

from typing import Any, Dict, List, Optional

from .block import BlockHeader
from .hasher import double_sha256, hash_to_int, to_display_hex
from .logger_setup import setup_logger
from .worker_client import WorkerClient, WorkerError
from .target import bits_to_target, hash_meets_target


def check_nonces(config: Dict[str, Any]) -> List[Dict[str, Any]]:
    logger = setup_logger(level=config.get("log_level", "INFO"))

    base_header = BlockHeader.from_config(config["block_header_template"])
    target = bits_to_target(base_header.bits)

    logger.info("Target: %064x", target)

    results = []
    for nonce in config["nonces_to_try"]:
        header = base_header.with_nonce(nonce)
        digest = double_sha256(header.serialize())
        hash_int = hash_to_int(digest)
        solved = hash_meets_target(hash_int, target)

        display_hash = to_display_hex(digest)
        logger.info("nonce=%s hash=%s solved=%s", nonce, display_hash, solved)

        results.append(
            {
                "nonce": nonce,
                "hash": display_hash,
                "solved": solved,
            }
        )

    return results


def run(config: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    logger = setup_logger(level=config.get("log_level", "INFO"))

    if not config.get("worker_url"):
        raise WorkerError("Configure worker_url and payout_address to check and submit live blocks")
    client = WorkerClient(config["worker_url"])
    candidate = client.candidate(config["payout_address"])
    config = {**config, "block_header_template": candidate["block_header_template"]}
    logger.info("Live candidate height=%s payouts=%s", candidate["height"], candidate["payouts"])

    results = check_nonces(config)
    winners = [r for r in results if r["solved"]]

    if not winners:
        logger.info("No nonce in the list solved the block (expected).")
        return None

    winner = winners[0]
    logger.info("Solving nonce found: %s", winner)

    result = client.submit(candidate["job"], winner["nonce"])
    if result.get("accepted") is not True:
        raise WorkerError(f"Block rejected: {result.get('result', 'unknown response')}")
    winner["submission"] = result
    logger.info("Node accepted block %s; rewards still require coinbase maturity.", result["hash"])

    return winner
