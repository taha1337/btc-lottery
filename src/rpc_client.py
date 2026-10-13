"""Makes JSON-RPC calls to a Bitcoin node."""

from typing import Any, Dict, Optional

import requests


class RpcError(RuntimeError):
    pass


class BitcoinRpcClient:
    def __init__(self, rpc_config: Dict[str, Any], timeout: int = 30):
        self.host = rpc_config.get("host", "127.0.0.1")
        self.port = rpc_config.get("port", 8332)
        self.user = rpc_config.get("user")
        self.password = rpc_config.get("password")
        self.api_key = rpc_config.get("api_key")
        self.timeout = timeout
        self.url = f"http://{self.host}:{self.port}/"

    def _headers(self) -> Dict[str, str]:
        headers = {"Content-Type": "application/json"}
        
        
        
        if self.api_key and self.api_key != "your_hosted_node_api_key_if_applicable":
            headers["X-API-Key"] = self.api_key
        return headers

    def _auth(self) -> Optional[tuple]:
        if self.user and self.password:
            return (self.user, self.password)
        return None

    def call(self, method: str, params: Optional[list] = None) -> Any:
        payload = {
            "jsonrpc": "1.0",
            "id": "btc-nonce-checker",
            "method": method,
            "params": params or [],
        }

        response = requests.post(
            self.url,
            json=payload,
            headers=self._headers(),
            auth=self._auth(),
            timeout=self.timeout,
        )
        response.raise_for_status()
        data = response.json()

        if data.get("error"):
            raise RpcError(f"{method} failed: {data['error']}")

        return data.get("result")

    def submit_block(self, block_hex: str) -> Any:
        return self.call("submitblock", [block_hex])

    def get_block_count(self) -> Any:
        return self.call("getblockcount")
