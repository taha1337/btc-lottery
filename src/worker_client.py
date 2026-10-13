"""Public Worker API; upstream node credentials never belong in this client."""

import requests


class WorkerError(RuntimeError):
    pass


class WorkerClient:
    def __init__(self, url, timeout=30):
        self.url = url.rstrip("/")
        self.timeout = timeout

    def _request(self, path, **kwargs):
        try:
            response = requests.request(timeout=self.timeout, url=self.url + path, **kwargs)
            data = response.json()
        except (requests.RequestException, ValueError) as exc:
            raise WorkerError("Worker unreachable or returned invalid JSON") from exc
        if not isinstance(data, dict):
            raise WorkerError("Worker returned an invalid response; expected a JSON object")
        if not response.ok or data.get("error"):
            raise WorkerError(str(data.get("error") or data.get("result") or f"Worker HTTP {response.status_code}"))
        return data

    def candidate(self, address):
        return self._request("/candidate-block", method="GET", params={"payout_address": address})

    def submit(self, job, nonce):
        return self._request("/submit-block", method="POST", json={"job": job, "nonce": nonce})
