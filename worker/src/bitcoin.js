import { Buffer } from 'node:buffer';
import { createHash } from 'node:crypto';
import { FEE_BPS } from './payout.js';

export const hash256 = data => createHash('sha256').update(createHash('sha256').update(data).digest()).digest();
const hex = value => Buffer.from(value, 'hex');
export function uint32(value) {
  const bytes = Buffer.alloc(4);
  bytes.writeUInt32LE(value >>> 0);
  return bytes;
}
export function compactSize(value) {
  if (value < 253) return Buffer.from([value]);
  if (value <= 65535) {
    const bytes = Buffer.alloc(3); bytes[0] = 253; bytes.writeUInt16LE(value, 1); return bytes;
  }
  return Buffer.concat([Buffer.from([254]), uint32(value)]);
}
function heightScript(height) {
  if (height >= 1 && height <= 16) return Buffer.from([0x50 + height]);
  const bytes = [];
  while (height > 0) { bytes.push(height % 256); height = Math.floor(height / 256); }
  if (bytes.at(-1) & 128) bytes.push(0);
  return Buffer.from([bytes.length, ...bytes]);
}
function output(satoshis, scriptHex) {
  const amount = Buffer.alloc(8);
  amount.writeBigUInt64LE(BigInt(satoshis));
  const script = hex(scriptHex);
  return Buffer.concat([amount, compactSize(script.length), script]);
}
export function merkleRoot(hashes) {
  while (hashes.length > 1) {
    const next = [];
    for (let i = 0; i < hashes.length; i += 2) {
      next.push(hash256(Buffer.concat([hashes[i], hashes[i + 1] || hashes[i]])));
    }
    hashes = next;
  }
  return hashes[0];
}
export function meetsTarget(header) {
  const bits = header.readUInt32LE(72);
  const exponent = bits >>> 24;
  const mantissa = BigInt(bits & 0x007fffff);
  const target = exponent <= 3 ? mantissa >> BigInt(8 * (3 - exponent)) : mantissa << BigInt(8 * (exponent - 3));
  if ((bits & 0x00800000) || target <= 0n || target >= (1n << 256n)) return false;
  const hash = Buffer.from(hash256(header)).reverse().toString('hex');
  return BigInt('0x' + hash) <= target;
}

export function buildBlock(template, ownerScript, userScript) {
  if (!Number.isSafeInteger(template.coinbasevalue) || template.coinbasevalue < 0) throw new Error('Invalid template reward');
  if ((template.rules || []).some(rule => rule.startsWith('!') && rule !== '!segwit')) throw new Error('Unsupported block rule');
  const ownerAmount = Number(BigInt(template.coinbasevalue) * BigInt(FEE_BPS) / 10000n);
  const userAmount = template.coinbasevalue - ownerAmount;
  const script = Buffer.concat([
    heightScript(template.height),
    ...Object.values(template.coinbaseaux || {}).map(hex),
    Buffer.from('/nonce-checker/'),
  ]);
  if (script.length > 100) throw new Error('Coinbase script too long');
  const input = Buffer.concat([compactSize(1), Buffer.alloc(32), hex('ffffffff'), compactSize(script.length), script, hex('ffffffff')]);
  const outputs = [];
  if (ownerAmount > 0) outputs.push(output(ownerAmount, ownerScript));
  outputs.push(output(userAmount, userScript));
  const commitment = template.default_witness_commitment;
  if (commitment) outputs.push(output(0, commitment));
  else if (template.transactions.some(tx => tx.hash !== tx.txid)) throw new Error('Missing witness commitment');
  const outputBytes = Buffer.concat([compactSize(outputs.length), ...outputs]);
  const strippedCoinbase = Buffer.concat([uint32(2), input, outputBytes, uint32(0)]);
  // The Merkle root uses the coinbase without witness bytes.
  const coinbase = commitment
    ? Buffer.concat([uint32(2), hex('0001'), input, outputBytes, hex('0120'), Buffer.alloc(32), uint32(0)])
    : strippedCoinbase;
  const root = merkleRoot([hash256(strippedCoinbase), ...template.transactions.map(tx => hex(tx.txid).reverse())]);
  const headerFields = {
    version: (template.version | (template.vbrequired || 0)) >>> 0,
    previous_block_hash: template.previousblockhash,
    merkle_root: Buffer.from(root).reverse().toString('hex'),
    timestamp: template.curtime, bits: template.bits,
  };
  const header = Buffer.concat([
    uint32(headerFields.version), hex(template.previousblockhash).reverse(), root,
    uint32(template.curtime), uint32(parseInt(template.bits, 16)), uint32(0),
  ]);
  const count = compactSize(template.transactions.length + 1);
  const block = Buffer.concat([header, count, coinbase, ...template.transactions.map(tx => hex(tx.data))]);
  const weight = (80 + count.length) * 4 + strippedCoinbase.length * 3 + coinbase.length
    + template.transactions.reduce((sum, tx) => sum + (tx.weight ?? hex(tx.data).length * 4), 0);
  if (weight > (template.weightlimit || 4000000) || block.length > (template.sizelimit || 4000000)) throw new Error('Block exceeds size limit');
  return { block, headerFields, ownerAmount, userAmount };
}
