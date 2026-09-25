const test = require('node:test');
const assert = require('node:assert/strict');
const { validPin, createPin, matchesPin, attemptState, LOCK_MS } = require('./pin-security');
const pepper = 'test-only-secret-at-least-32-characters-long';

test('accepts exactly four ASCII digits, including leading zeros', () => {
  assert.equal(validPin('0042'), true);
  for (const value of [1234, '123', '12345', 'abcd', '১২৩৪', ' 1234', null]) assert.equal(validPin(value), false);
});

test('salted hashes verify only the matching PIN, UID and server secret', async () => {
  const record = await createPin('0042', 'alice', pepper);
  const other = await createPin('0042', 'alice', pepper);
  assert.notEqual(record.hash, other.hash);
  assert.notEqual(record.salt, other.salt);
  assert.equal(await matchesPin('0042', 'alice', record, pepper), true);
  assert.equal(await matchesPin('0043', 'alice', record, pepper), false);
  assert.equal(await matchesPin('0042', 'bob', record, pepper), false);
  assert.equal(await matchesPin('0042', 'alice', record, pepper + 'wrong'), false);
  assert.equal(await matchesPin('0042', 'alice', { ...record, hash: 'broken' }, pepper), false);
});

test('five failures lock entry; correct PIN cannot bypass an active lock', () => {
  let record = {};
  for (let i = 1; i <= 5; i++) {
    record = attemptState(record, false, 1000);
    assert.equal(record.failedAttempts, i);
  }
  assert.equal(record.lockedUntil, 1000 + LOCK_MS);
  assert.equal(attemptState(record, true, 2000).locked, true);
  assert.deepEqual(attemptState(record, false, 1001 + LOCK_MS), { failedAttempts: 1, lockedUntil: 0 });
});

test('successful verification resets attempts', () => {
  assert.deepEqual(attemptState({ failedAttempts: 4 }, true, 1000), { failedAttempts: 0, lockedUntil: 0 });
});
