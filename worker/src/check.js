import { Buffer } from 'node:buffer';
import { hash256, meetsTarget } from './bitcoin.js';

export const MAX_NONCES = 128;

export function validateNonces(nonces) {
  if (!Array.isArray(nonces) || !nonces.length || nonces.length > MAX_NONCES) {
    throw new Error(`Enter between 1 and ${MAX_NONCES} nonce guesses`);
  }
  if (nonces.some(n => !Number.isInteger(n) || n < 0 || n > 0xffffffff)) {
    throw new Error('Each nonce must be a whole number from 0 to 4294967295');
  }
  return [...new Set(nonces)];
}

export function checkHeader(header, nonces) {
  const bytes = Buffer.from(header);
  if (bytes.length !== 80) throw new Error('Invalid block header');
  return nonces.map(nonce => {
    bytes.writeUInt32LE(nonce, 76);
    return { nonce, hash: Buffer.from(hash256(bytes)).reverse().toString('hex'), solved: meetsTarget(bytes) };
  });
}
