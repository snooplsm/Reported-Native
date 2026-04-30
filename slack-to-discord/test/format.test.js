import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { renderSlackText, splitDiscordContent } from '../src/format.js';

describe('renderSlackText', () => {
  it('converts Slack users, channels, links, and entities', async () => {
    const slack = {
      displayUser: async (id) => `user-${id}`,
      displayChannel: async (id) => `channel-${id}`
    };

    const result = await renderSlackText(
      'hi <@U123> in <#C123> see <https://example.com|example> &amp; ok',
      slack
    );

    assert.equal(result, 'hi @user-U123 in #channel-C123 see example (https://example.com) & ok');
  });
});

describe('splitDiscordContent', () => {
  it('splits long content under Discord limits', () => {
    const chunks = splitDiscordContent('x'.repeat(4500));
    assert.equal(chunks.length, 3);
    assert.ok(chunks.every((chunk) => chunk.length <= 2000));
  });
});
