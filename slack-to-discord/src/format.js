const DISCORD_CONTENT_LIMIT = 2000;

export function slackTimestampToIso(ts) {
  const seconds = Number(String(ts || '').split('.')[0]);
  if (!Number.isFinite(seconds)) return new Date().toISOString();
  return new Date(seconds * 1000).toISOString();
}

export function slackPermalinkTs(ts) {
  return String(ts || '').replace('.', '');
}

export async function renderSlackText(text = '', slack) {
  let rendered = text;

  rendered = await replaceAsync(rendered, /<@([A-Z0-9]+)>/g, async (_match, userId) => {
    return `@${await slack.displayUser(userId)}`;
  });

  rendered = await replaceAsync(rendered, /<#([A-Z0-9]+)(?:\|([^>]+))?>/g, async (_match, channelId, label) => {
    return `#${label || (await slack.displayChannel(channelId))}`;
  });

  rendered = rendered.replace(/<!here>/g, '@here');
  rendered = rendered.replace(/<!channel>/g, '@channel');
  rendered = rendered.replace(/<!everyone>/g, '@everyone');

  rendered = rendered.replace(/<((?:https?|mailto):[^>|]+)\|([^>]+)>/g, '$2 ($1)');
  rendered = rendered.replace(/<((?:https?|mailto):[^>]+)>/g, '$1');
  rendered = rendered.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');

  return rendered.trim();
}

export function splitDiscordContent(content) {
  const source = content || '(no text)';
  if (source.length <= DISCORD_CONTENT_LIMIT) return [source];

  const chunks = [];
  let remaining = source;
  while (remaining.length > 0) {
    let cut = Math.min(DISCORD_CONTENT_LIMIT, remaining.length);
    const newline = remaining.lastIndexOf('\n', cut);
    if (newline > 500) cut = newline;
    chunks.push(remaining.slice(0, cut));
    remaining = remaining.slice(cut).trimStart();
  }
  return chunks;
}

export async function messagePayloads({ event, slack, channelName, mode = 'message', fileNotes = [] }) {
  const message = mode === 'deleted' ? event.previous_message || event : event.message || event;
  const baseText = await renderSlackText(message.text || event.text || '', slack);
  const prefix =
    mode === 'edited' ? '**Edited in Slack**\n' : mode === 'deleted' ? '**Deleted in Slack**\n' : '';
  const suffix = fileNotes.length ? `\n\n${fileNotes.join('\n')}` : '';
  const chunks = splitDiscordContent(`${prefix}${baseText}${suffix}`.trim());

  const user = await messageAuthor(message, slack);
  const threadText =
    message.thread_ts && message.thread_ts !== message.ts ? ` · thread ${message.thread_ts}` : '';
  const timestamp = slackTimestampToIso(message.ts || event.event_ts);

  return chunks.map((content, index) => ({
    username: user.username,
    avatar_url: user.avatarUrl,
    content: index === 0 ? content : `continued:\n${content}`,
    allowed_mentions: { parse: [] },
    embeds:
      index === 0
        ? [
            {
              color: mode === 'deleted' ? 0xcc3d3d : mode === 'edited' ? 0xe0a82e : 0x5865f2,
              timestamp,
              footer: {
                text: `Slack #${channelName} · ts ${message.ts || event.event_ts}${threadText}`
              }
            }
          ]
        : []
  }));
}

async function messageAuthor(message, slack) {
  if (message.bot_profile || message.username) {
    return {
      username: `${message.bot_profile?.name || message.username || 'Slack bot'} (Slack)`,
      avatarUrl: message.bot_profile?.icons?.image_72
    };
  }

  const user = await slack.getUser(message.user);
  const profile = user?.profile || {};
  return {
    username: `${profile.display_name || profile.real_name || user?.name || message.user || 'Slack'} (Slack)`,
    avatarUrl: profile.image_72 || profile.image_192
  };
}

async function replaceAsync(value, regex, replacer) {
  const matches = [...value.matchAll(regex)];
  const replacements = await Promise.all(matches.map((match) => replacer(...match)));
  let index = 0;
  return value.replace(regex, () => replacements[index++]);
}
