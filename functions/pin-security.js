const { randomBytes, createHmac, scrypt, timingSafeEqual } = require('node:crypto');
const { promisify } = require('node:util');
const derive = promisify(scrypt);
const MAX_ATTEMPTS = 5;
const LOCK_MS = 15 * 60 * 1000;

function validPin(pin) {
  return typeof pin === 'string' && /^[0-9]{4}$/.test(pin);
}

async function digest(pin, uid, salt, pepper) {
  if (!validPin(pin) || typeof pepper !== 'string' || pepper.length < 32) {
    throw new Error('Invalid PIN or server secret configuration');
  }
  const input = createHmac('sha256', pepper).update(`${uid}:${pin}`).digest();
  return derive(input, salt, 32);
}

async function createPin(pin, uid, pepper) {
  const salt = randomBytes(16).toString('hex');
  return { salt, hash: (await digest(pin, uid, salt, pepper)).toString('hex'), version: 1 };
}

async function matchesPin(pin, uid, record, pepper) {
  if (!validPin(pin) || record.version !== 1 || !/^[a-f0-9]{64}$/.test(record.hash) ||
      !/^[a-f0-9]{32}$/.test(record.salt)) return false;
  const expected = Buffer.from(record.hash, 'hex');
  return timingSafeEqual(await digest(pin, uid, record.salt, pepper), expected);
}

function attemptState(record, matched, now) {
  if ((record.lockedUntil || 0) > now) return { locked: true, lockedUntil: record.lockedUntil };
  if (matched) return { failedAttempts: 0, lockedUntil: 0 };
  const failures = (record.lockedUntil ? 0 : (record.failedAttempts || 0)) + 1;
  return { failedAttempts: failures, lockedUntil: failures >= MAX_ATTEMPTS ? now + LOCK_MS : 0 };
}

module.exports = { validPin, createPin, matchesPin, attemptState, MAX_ATTEMPTS, LOCK_MS };
