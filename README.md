# BTC Lottery

Checks nonce guesses against a Bitcoin block and can submit a winning block through a credential-protected Worker.

Requires Python 3. The Worker is already hosted on Cloudflare.

## Install

```powershell
python -m pip install -r requirements.txt
```

## Run

Create the config if it doesn't exist:

```powershell
if (!(Test-Path config/miner_config.local.json)) {
  Copy-Item config/worker_config.example.json config/miner_config.local.json
}
```

In `config/miner_config.local.json`, enter the user's receiving address in
`payout_address` and guesses in `nonces_to_try`. Nonces must be integers from
`0` to `4294967295`. The config already points to the hosted Worker:
`https://btc-hash-credential-server.btc-nonce-668442ed.workers.dev`.

```powershell
python main.py --config config/miner_config.local.json
```

Add `--submit` to submit a winning guess:

```powershell
python main.py --config config/miner_config.local.json --submit
```

The fee is fixed at 5%; the user receives the remaining 95%.
