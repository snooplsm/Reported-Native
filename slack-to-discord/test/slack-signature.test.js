import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { verifySlackSignature } from '../src/slack.js';

describe('verifySlackSignature', () => {
  it('accepts a valid Slack v0 signature', () => {
    const signingSecret = 'secret';
    const timestamp = String(Math.floor(Date.now() / 1000));
    const rawBody = '{"type":"event_callback"}';
    const signature = `v0=${createHmac('sha256', signingSecret)
      .update(`v0:${timestamp}:${rawBody}`)
      .digest('hex')}`;

    assert.equal(
      verifySlackSignature({ signingSecret, timestamp, signature, rawBody }),
      true
    );
  });

  it('rejects an invalid signature', () => {
    assert.equal(
      verifySlackSignature({
        signingSecret: 'secret',
        timestamp: String(Math.floor(Date.now() / 1000)),
        signature: 'v0=bad',
        rawBody: '{}'
      }),
      false
    );
  });
});
