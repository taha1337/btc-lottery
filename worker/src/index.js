import { Buffer } from 'node:buffer';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { buildBlock, hash256, meetsTarget } from './bitcoin.js';
import { FEE_BPS, OWNER_BTC_ADDRESS } from './payout.js';
import { checkHeader, validateNonces, MAX_NONCES } from './check.js';

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const json = (data, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });

async function rpc(env, method, params = []) {
  let response, data;
  try {
    response = await fetch(env.BTC_NODE_URL.trim(), {
      method: 'POST', redirect: 'manual', signal: AbortSignal.timeout(25000),
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }),
    });
    data = await response.json();
  } catch (error) {
    console.error('Bitcoin RPC failure', error.name, response?.status ?? 'no response');
    throw new ApiError(502, 'Bitcoin node unavailable or returned non-JSON data; check BTC_NODE_URL');
  }
  // Upstream errors can contain credentials.
  if (!response.ok || !data || data.error || !Object.hasOwn(data, 'result')) throw new ApiError(502, `Bitcoin node rejected ${method}`);
  return data.result;
}
async function addressScript(env, address) {
  if (typeof address !== 'string' || address.length < 14 || address.length > 90 || !/^[a-zA-Z0-9]+$/.test(address)) throw new ApiError(400, 'Set a valid Bitcoin receiving address');
  const result = await rpc(env, 'validateaddress', [address]);
  if (!result?.isvalid || !result.scriptPubKey) throw new ApiError(400, 'Address is invalid for the Bitcoin node network');
  return result.scriptPubKey;
}
export function signJob(job, secret) {
  return createHmac('sha256', secret).update(`${OWNER_BTC_ADDRESS}:${FEE_BPS}:${job.expires}:${job.block_hex}`).digest('hex');
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (url.pathname === '/' && request.method === 'GET') return json({ service: 'btc-hash-credential-server', api_version: 2, max_nonces: MAX_NONCES, endpoints: ['GET /candidate-block?payout_address=...', 'POST /submit-block', 'POST /check-nonces'] });
      if (!['/candidate-block', '/submit-block', '/check-nonces'].includes(url.pathname)) return json({ error: 'Not found' }, 404);
      const method = url.pathname === '/candidate-block' ? 'GET' : 'POST';
      if (request.method !== method) throw new ApiError(405, `Use ${method}`);
      if (!env.BTC_NODE_URL || !env.JOB_SIGNING_SECRET || env.JOB_SIGNING_SECRET.length < 32) throw new ApiError(503, 'Configure BTC_NODE_URL and JOB_SIGNING_SECRET (32+ characters) on the Worker');

      if (url.pathname === '/check-nonces') {
        if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw new ApiError(415, 'Use application/json');
        const reader = request.body?.getReader();
        if (!reader) throw new ApiError(400, 'Request body required');
        const chunks = []; let size = 0;
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          size += value.length;
          if (size > 4096) { await reader.cancel(); throw new ApiError(413, 'Request too large'); }
          chunks.push(Buffer.from(value));
        }
        let body, nonces;
        try {
          body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
          nonces = validateNonces(body?.nonces);
        } catch (error) { throw new ApiError(400, error instanceof SyntaxError ? 'Invalid JSON' : error.message); }
        const address = body.payout_address;
        const userScript = await addressScript(env, address);
        const ownerScript = await addressScript(env, OWNER_BTC_ADDRESS);
        const template = await rpc(env, 'getblocktemplate', [{ rules: ['segwit'] }]);
        const candidate = buildBlock(template, ownerScript, userScript);
        const results = checkHeader(candidate.block.subarray(0, 80), nonces);
        const winner = results.find(result => result.solved);
        const response = {
          height: template.height, checked: results.length, duplicates_skipped: body.nonces.length - nonces.length,
          checked_at: new Date().toISOString(), results,
          payouts: { owner_address: OWNER_BTC_ADDRESS, user_address: address, fee_bps: FEE_BPS,
            owner_satoshis: candidate.ownerAmount, user_satoshis: candidate.userAmount },
          submission: { status: 'not_solved' },
        };
        if (winner) {
          if (await rpc(env, 'getbestblockhash') !== template.previousblockhash) {
            response.submission = { status: 'stale', hash: winner.hash };
          } else {
            candidate.block.writeUInt32LE(winner.nonce, 76);
            try {
              const result = await rpc(env, 'submitblock', [candidate.block.toString('hex')]);
              response.submission = { status: result === null ? 'accepted' : 'rejected', hash: winner.hash };
            } catch {
              // A timeout does not establish whether the node received the block.
              response.submission = { status: 'unknown', hash: winner.hash };
            }
          }
        }
        return json(response);
      }

      if (url.pathname === '/candidate-block') {
        const address = url.searchParams.get('payout_address');
        const userScript = await addressScript(env, address);
        const ownerScript = await addressScript(env, OWNER_BTC_ADDRESS);
        const template = await rpc(env, 'getblocktemplate', [{ rules: ['segwit'] }]);
        const candidate = buildBlock(template, ownerScript, userScript);
        const job = { block_hex: candidate.block.toString('hex'), expires: Math.floor(Date.now() / 1000) + 120 };
        job.signature = signJob(job, env.JOB_SIGNING_SECRET);
        return json({ job, height: template.height, block_header_template: candidate.headerFields,
          payouts: { owner_address: OWNER_BTC_ADDRESS, user_address: address, fee_bps: FEE_BPS, owner_satoshis: candidate.ownerAmount, user_satoshis: candidate.userAmount } });
      }

      const text = await request.text();
      if (text.length > 9000000) throw new ApiError(413, 'Request too large');
      let body;
      try { body = JSON.parse(text); } catch { throw new ApiError(400, 'Invalid JSON'); }
      if (!body || !Number.isInteger(body.nonce) || body.nonce < 0 || body.nonce > 0xffffffff) throw new ApiError(400, 'nonce must be an unsigned 32-bit integer');
      const job = body.job;
      if (!job || typeof job.block_hex !== 'string' || !/^(?:[0-9a-f]{2})+$/.test(job.block_hex) || !/^[0-9a-f]{64}$/.test(job.signature || '') || !Number.isSafeInteger(job.expires)) throw new ApiError(400, 'Invalid candidate job');
      if (!timingSafeEqual(Buffer.from(signJob(job, env.JOB_SIGNING_SECRET), 'hex'), Buffer.from(job.signature, 'hex'))) throw new ApiError(400, 'Candidate was modified');
      if (job.expires <= Math.floor(Date.now() / 1000)) throw new ApiError(409, 'Candidate expired; run again for a fresh candidate');
      const block = Buffer.from(job.block_hex, 'hex');
      if (block.length <= 80) throw new ApiError(400, 'Candidate must contain a full block');
      block.writeUInt32LE(body.nonce, 76);
      if (!meetsTarget(block.subarray(0, 80))) throw new ApiError(422, 'Nonce does not meet the Bitcoin target');
      const previousHash = Buffer.from(block.subarray(4, 36)).reverse().toString('hex');
      if (await rpc(env, 'getbestblockhash') !== previousHash) throw new ApiError(409, 'Candidate is stale; run again for a fresh candidate');
      const result = await rpc(env, 'submitblock', [block.toString('hex')]);
      const hash = Buffer.from(hash256(block.subarray(0, 80))).reverse().toString('hex');
      return json({ accepted: result === null, result, hash }, result === null ? 200 : 422);
    } catch (error) {
      return json({ error: error instanceof ApiError ? error.message : 'Unable to build or verify Bitcoin block' }, error instanceof ApiError ? error.status : 502);
    }
  },
};
