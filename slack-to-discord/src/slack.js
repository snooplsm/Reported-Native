import { createHmac, timingSafeEqual } from 'node:crypto';

const SLACK_API_BASE = 'https://slack.com/api';

export function verifySlackSignature({ signingSecret, timestamp, signature, rawBody }) {
  if (!timestamp || !signature) return false;

  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - Number(timestamp)) > 60 * 5) return false;

  const basestring = `v0:${timestamp}:${rawBody}`;
  const digest = `v0=${createHmac('sha256', signingSecret).update(basestring).digest('hex')}`;
  const digestBuffer = Buffer.from(digest);
  const signatureBuffer = Buffer.from(signature);

  return (
    digestBuffer.length === signatureBuffer.length &&
    timingSafeEqual(digestBuffer, signatureBuffer)
  );
}

export class SlackClient {
  constructor(token) {
    this.token = token;
    this.userCache = new Map();
    this.channelCache = new Map();
  }

  async api(method, params = {}) {
    while (true) {
      const body = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null) {
          body.set(key, String(value));
        }
      }

      const response = await fetch(`${SLACK_API_BASE}/${method}`, {
        method: 'POST',
        headers: {
          authorization: `Bearer ${this.token}`,
          'content-type': 'application/x-www-form-urlencoded'
        },
        body
      });

      if (response.status === 429) {
        const retryAfterSeconds = Number(response.headers.get('retry-after') || 60);
        await sleep((retryAfterSeconds + 1) * 1000);
        continue;
      }

      const json = await response.json();
      if (!json.ok) {
        throw new Error(`Slack ${method} failed: ${json.error || response.statusText}`);
      }
      return json;
    }
  }

  async paginate(method, params, collectionKey) {
    const items = [];
    let cursor;
    do {
      const page = await this.api(method, { ...params, cursor, limit: 200 });
      items.push(...(page[collectionKey] || []));
      cursor = page.response_metadata?.next_cursor || '';
    } while (cursor);
    return items;
  }

  async listConversations(types) {
    return this.paginate(
      'conversations.list',
      { types: types.join(','), exclude_archived: true },
      'channels'
    );
  }

  async joinConversation(channelId) {
    return this.api('conversations.join', { channel: channelId });
  }

  async history({ channel, oldest, latest, cursor, limit = 15 }) {
    return this.api('conversations.history', {
      channel,
      oldest,
      latest,
      cursor,
      limit,
      inclusive: true
    });
  }

  async replies({ channel, ts, oldest, latest, cursor, limit = 15 }) {
    return this.api('conversations.replies', {
      channel,
      ts,
      oldest,
      latest,
      cursor,
      limit,
      inclusive: true
    });
  }

  async getUser(userId) {
    if (!userId) return null;
    if (!this.userCache.has(userId)) {
      const json = await this.api('users.info', { user: userId });
      this.userCache.set(userId, json.user);
    }
    return this.userCache.get(userId);
  }

  async getChannel(channelId) {
    if (!channelId) return null;
    if (!this.channelCache.has(channelId)) {
      const json = await this.api('conversations.info', { channel: channelId });
      this.channelCache.set(channelId, json.channel);
    }
    return this.channelCache.get(channelId);
  }

  async displayUser(userId) {
    const user = await this.getUser(userId);
    if (!user) return userId || 'Unknown';
    return user.profile?.display_name || user.profile?.real_name || user.name || userId;
  }

  async displayChannel(channelId) {
    const channel = await this.getChannel(channelId);
    return channel?.name || channelId;
  }

  async downloadFile(file) {
    const url = file.url_private_download || file.url_private;
    if (!url) return null;

    const response = await fetch(url, {
      headers: { authorization: `Bearer ${this.token}` }
    });
    if (!response.ok) {
      throw new Error(`Slack file download failed: ${response.status} ${response.statusText}`);
    }

    return {
      bytes: await response.arrayBuffer(),
      filename: file.name || file.title || `${file.id || 'slack-file'}`,
      contentType: response.headers.get('content-type') || file.mimetype || 'application/octet-stream'
    };
  }
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
