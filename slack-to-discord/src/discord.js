const DISCORD_API_BASE = 'https://discord.com/api/v10';

export function normalizeDiscordChannelName(name, prefix = '') {
  const normalized = name
    .toLowerCase()
    .replace(/[^a-z0-9-_]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 90);
  return `${prefix}${normalized || 'channel'}`.slice(0, 100);
}

async function discordFetch(path, options = {}, token) {
  const botToken = normalizeBotToken(token);
  const response = await fetch(`${DISCORD_API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'content-type': 'application/json' }),
      ...(botToken ? { authorization: `Bot ${botToken}` } : {}),
      ...options.headers
    }
  });

  if (response.status === 429) {
    const retry = await response.json();
    const retryMs = Math.ceil((retry.retry_after || 1) * 1000);
    await new Promise((resolve) => setTimeout(resolve, retryMs));
    return discordFetch(path, options, token);
  }

  if (!response.ok) {
    const body = await response.text();
    if (response.status === 403 && body.includes('Missing Permissions')) {
      throw new Error(
        `Discord API failed 403: Missing Permissions. Reinvite the bot with View Channels, Manage Channels, Manage Webhooks, Send Messages, Attach Files, and Read Message History, then make sure the bot role is not blocked by server/category permission overrides. Original response: ${body}`
      );
    }
    if (response.status === 401) {
      throw new Error(
        'Discord API failed 401: Unauthorized. DISCORD_BOT_TOKEN is not a valid current bot token for this Discord app. Copy it from Discord Developer Portal -> your app -> Bot -> Token, not the client secret, public key, or invite URL.'
      );
    }
    throw new Error(`Discord API failed ${response.status}: ${body}`);
  }

  if (response.status === 204) return null;
  return response.json();
}

export class DiscordAdminClient {
  constructor(token) {
    this.token = normalizeBotToken(token);
  }

  async getGuildChannels(guildId) {
    return discordFetch(`/guilds/${guildId}/channels`, {}, this.token);
  }

  async createGuildChannel(guildId, body) {
    return discordFetch(
      `/guilds/${guildId}/channels`,
      { method: 'POST', body: JSON.stringify(body) },
      this.token
    );
  }

  async createWebhook(channelId, name) {
    return discordFetch(
      `/channels/${channelId}/webhooks`,
      { method: 'POST', body: JSON.stringify({ name }) },
      this.token
    );
  }

  async getChannelMessages(channelId, limit = 100, before) {
    const params = new URLSearchParams({
      limit: String(Math.min(Math.max(limit, 1), 100))
    });
    if (before) params.set('before', before);
    const url = `/channels/${channelId}/messages?${params}`;
    return discordFetch(url, {}, this.token);
  }

  webhookUrl(webhook) {
    return `https://discord.com/api/webhooks/${webhook.id}/${webhook.token}`;
  }
}

function normalizeBotToken(token) {
  return String(token || '').trim().replace(/^Bot\s+/i, '');
}

export class DiscordWebhookClient {
  async execute(webhookUrl, payload, files = []) {
    const url = new URL(webhookUrl);
    url.searchParams.set('wait', 'true');

    const body = files.length > 0 ? this.multipartBody(payload, files) : JSON.stringify(payload);
    const response = await fetch(url, {
      method: 'POST',
      headers: files.length > 0 ? {} : { 'content-type': 'application/json' },
      body
    });

    if (response.status === 429) {
      const retry = await response.json();
      const retryMs = Math.ceil((retry.retry_after || 1) * 1000);
      await new Promise((resolve) => setTimeout(resolve, retryMs));
      return this.execute(webhookUrl, payload, files);
    }

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Discord webhook failed ${response.status}: ${text}`);
    }

    return response.json();
  }

  async deleteMessage(webhookUrl, messageId) {
    const webhook = parseWebhookUrl(webhookUrl);
    const response = await fetch(
      `${DISCORD_API_BASE}/webhooks/${webhook.id}/${webhook.token}/messages/${messageId}`,
      { method: 'DELETE' }
    );

    if (response.status === 429) {
      const retry = await response.json();
      const retryMs = Math.ceil((retry.retry_after || 1) * 1000);
      await new Promise((resolve) => setTimeout(resolve, retryMs));
      return this.deleteMessage(webhookUrl, messageId);
    }

    if (!response.ok && response.status !== 404) {
      const text = await response.text();
      throw new Error(`Discord webhook delete failed ${response.status}: ${text}`);
    }
  }

  multipartBody(payload, files) {
    const form = new FormData();
    form.set('payload_json', JSON.stringify(payload));
    files.forEach((file, index) => {
      form.set(
        `files[${index}]`,
        new Blob([file.bytes], { type: file.contentType }),
        file.filename
      );
    });
    return form;
  }
}

function parseWebhookUrl(webhookUrl) {
  const url = new URL(webhookUrl);
  const parts = url.pathname.split('/').filter(Boolean);
  const webhookIndex = parts.indexOf('webhooks');
  const id = parts[webhookIndex + 1];
  const token = parts[webhookIndex + 2];
  if (!id || !token) {
    throw new Error('Invalid Discord webhook URL');
  }
  return { id, token };
}
