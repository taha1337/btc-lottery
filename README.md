# BTC Lottery

Checks nonce guesses against a Bitcoin block and can submit a winning block using a Worker.

Requires Python 3. Worker already hosted on Cloudflare.

## Install
```
python -m pip install -r requirements.txt
```

## Run
```
python main.py
```

5% fee goes to author

## Android app

Open `android/` in Android Studio, or follow [the build instructions](android/README.md).

Enter your Bitcoin receiving address and up to 128 nonce guesses. The hosted Worker checks the numbers and automatically submits a valid result. The app saves your last 20 checks on your phone.
