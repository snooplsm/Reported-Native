import {
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  readFileSync,
  renameSync,
  unlinkSync,
  writeFileSync
} from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { backfillConfig } from './config.js';
import { DiscordAdminClient, DiscordWebhookClient } from './discord.js';
import { messagePayloads } from './format.js';
import { SlackClient, sleep } from './slack.js';
import { JsonStore } from './store.js';

const config = backfillConfig();
const store = new JsonStore(config.dataDir);
const slack = new SlackClient(config.slackBotToken);
const discord = new DiscordWebhookClient();
const discordAdmin = config.discordBotToken ? new DiscordAdminClient(config.discordBotToken) : null;
const releaseLock = acquireLock();

const statePath = resolve(process.cwd(), config.dataDir, 'backfill-state.json');
const state = config.useState ? readJson(statePath, { messages: {} }) : { messages: {} };
const map = store.readChannelMap();
const mappings = Object.entries(map.channels || {})
  .map(([slackChannelId, mapping]) => ({ slackChannelId, ...mapping }))
  .filter((mapping) => mapping.webhookUrl)
  .filter(matchesChannelFilter);

if (mappings.length === 0) {
  console.error('No mapped channels matched. Run npm run sync:channels first.');
  process.exit(1);
}

const nowSeconds = Math.floor(Date.now() / 1000);
const oldest =
  config.oldestTs ||
  String(nowSeconds - (config.minutes > 0 ? config.minutes * 60 : config.days * 24 * 60 * 60));
const latest = config.latestTs || String(nowSeconds);

console.log(
  `Backfilling ${mappings.length} channel(s), oldest=${oldest}, latest=${latest}, pageLimit=${config.pageLimit}`
);
console.log('This can be slow because Slack history APIs are tightly rate limited for newer apps.');
if (config.dedupe === 'discord') {
  if (!discordAdmin) {
    console.error('BACKFILL_DEDUPE=discord requires DISCORD_BOT_TOKEN so recent Discord messages can be checked.');
    process.exit(1);
  }
  console.log(`Deduping by scanning the last ${config.discordScanLimit} Discord messages per mapped channel.`);
} else if (config.useState) {
  console.log(`Deduping with local state file: ${statePath}`);
} else {
  console.warn('No dedupe mode is enabled. This can repost duplicates.');
}

for (const mapping of mappings) {
  await backfillChannel(mapping);
}

if (config.useState) writeJson(statePath, state);
console.log('Backfill complete.');
releaseLock();

async function backfillChannel(mapping) {
  console.log(`Fetching Slack #${mapping.slackChannelName || mapping.slackChannelId}`);
  const discordSeenTs = await loadDiscordSeenTs(mapping);
  const messages = [];
  let cursor = '';
  let page = 0;

  do {
    const response = await slack.history({
      channel: mapping.slackChannelId,
      oldest,
      latest,
      cursor,
      limit: config.pageLimit
    });

    page += 1;
    messages.push(...(response.messages || []));
    cursor = response.response_metadata?.next_cursor || '';
    console.log(
      `Fetched page ${page} from #${mapping.slackChannelName}: ${response.messages?.length || 0} message(s)`
    );

    if (cursor) {
      if (config.maxPages > 0 && page >= config.maxPages) {
        console.warn(
          `Stopping #${mapping.slackChannelName} after ${page} page(s) because BACKFILL_MAX_PAGES=${config.maxPages}`
        );
        break;
      }
      await sleep(config.historyDelayMs);
    }
  } while (cursor);

  messages.sort((a, b) => Number(a.ts) - Number(b.ts));
  for (const message of messages) {
    await mirrorHistoricalMessage(mapping, message, discordSeenTs);
    if (config.includeThreads && message.reply_count > 0) {
      await mirrorThreadReplies(mapping, message, discordSeenTs);
    }
  }

  if (config.useState) writeJson(statePath, state);
}

async function mirrorThreadReplies(mapping, parentMessage, discordSeenTs) {
  let cursor = '';
  let page = 0;

  do {
    const response = await slack.replies({
      channel: mapping.slackChannelId,
      ts: parentMessage.thread_ts || parentMessage.ts,
      oldest,
      latest,
      cursor,
      limit: config.pageLimit
    });

    page += 1;
    const replies = (response.messages || [])
      .filter((reply) => reply.ts !== parentMessage.ts)
      .sort((a, b) => Number(a.ts) - Number(b.ts));

    if (replies.length > 0) {
      console.log(
        `Fetched thread page ${page} from #${mapping.slackChannelName} parent ${parentMessage.ts}: ${replies.length} repl${replies.length === 1 ? 'y' : 'ies'}`
      );
    }

    for (const reply of replies) {
      await mirrorHistoricalMessage(mapping, reply, discordSeenTs);
    }

    cursor = response.response_metadata?.next_cursor || '';
    if (cursor) {
      if (config.maxPages > 0 && page >= config.maxPages) {
        console.warn(
          `Stopping thread ${parentMessage.ts} after ${page} page(s) because BACKFILL_MAX_PAGES=${config.maxPages}`
        );
        break;
      }
      await sleep(config.historyDelayMs);
    }
  } while (cursor);
}

async function mirrorHistoricalMessage(mapping, message, discordSeenTs) {
  if (!shouldMirrorHistoricalMessage(message)) return;

  const key = `${mapping.slackChannelId}:${message.ts}`;
  if (config.useState && state.messages[key]) return;
  if (discordSeenTs.has(message.ts)) {
    console.log(`Skipping already mirrored #${mapping.slackChannelName} ${message.ts}`);
    return;
  }

  const { discordFiles, fileNotes } = await prepareFiles(message.files || []);
  const payloads = await messagePayloads({
    event: {
      ...message,
      channel: mapping.slackChannelId,
      event_ts: message.ts
    },
    slack,
    channelName: mapping.slackChannelName || mapping.slackChannelId,
    fileNotes
  });

  payloads[0].content = `**Backfill**\n${payloads[0].content}`;
  for (let index = 0; index < payloads.length; index += 1) {
    await discord.execute(mapping.webhookUrl, payloads[index], index === 0 ? discordFiles : []);
  }

  state.messages[key] = {
    channel: mapping.slackChannelName || mapping.slackChannelId,
    ts: message.ts,
    mirroredAt: new Date().toISOString()
  };
  if (config.useState) writeJson(statePath, state);
  discordSeenTs.add(message.ts);
  console.log(`Mirrored #${mapping.slackChannelName} ${message.ts}`);
}

async function loadDiscordSeenTs(mapping) {
  const seen = new Set();
  if (config.dedupe !== 'discord') return seen;

  const messages = await discordAdmin.getChannelMessages(
    mapping.discordChannelId,
    config.discordScanLimit
  );

  for (const message of messages) {
    for (const embed of message.embeds || []) {
      const footerText = embed.footer?.text || '';
      const match = footerText.match(/\bts (\d+\.\d+)\b/);
      if (match) seen.add(match[1]);
    }
  }

  return seen;
}

function shouldMirrorHistoricalMessage(message) {
  if (!config.includeBotMessages && message.subtype === 'bot_message') return false;
  if (!message.subtype) return true;
  return ['bot_message', 'file_share', 'thread_broadcast'].includes(message.subtype);
}

async function prepareFiles(files) {
  if (!config.mirrorFiles || files.length === 0) {
    return { discordFiles: [], fileNotes: [] };
  }

  const discordFiles = [];
  const fileNotes = [];
  for (const file of files.slice(0, 10)) {
    const size = Number(file.size || 0);
    if (size > config.maxUploadBytes) {
      fileNotes.push(`Skipped file over Discord upload limit: ${file.name || file.title || file.id}`);
      continue;
    }

    try {
      discordFiles.push(await slack.downloadFile(file));
    } catch (error) {
      fileNotes.push(`Could not mirror file: ${file.name || file.title || file.id}`);
      console.warn(error.message);
    }
  }

  if (files.length > 10) {
    fileNotes.push(`Skipped ${files.length - 10} extra files because Discord allows 10 files per webhook message.`);
  }

  return { discordFiles: discordFiles.filter(Boolean), fileNotes };
}

function matchesChannelFilter(mapping) {
  if (config.channelFilter.length === 0) return true;
  const names = [
    mapping.slackChannelId,
    mapping.slackChannelName,
    mapping.discordChannelName
  ]
    .filter(Boolean)
    .map((value) => value.toLowerCase());
  return config.channelFilter.some((filter) => names.includes(filter));
}

function readJson(path, fallback) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') return fallback;
    throw error;
  }
}

function writeJson(path, value) {
  mkdirSync(dirname(path), { recursive: true });
  const tempPath = `${path}.tmp`;
  writeFileSync(tempPath, `${JSON.stringify(value, null, 2)}\n`);
  renameSync(tempPath, path);
}

function acquireLock() {
  if (!config.lock) return () => {};

  const lockPath = resolve(process.cwd(), config.dataDir, 'backfill.lock');
  mkdirSync(dirname(lockPath), { recursive: true });

  if (existsSync(lockPath)) {
    const lock = readJson(lockPath, null);
    const staleAfterMs = config.lockStaleMinutes * 60 * 1000;
    if (lock?.startedAt && Date.now() - Date.parse(lock.startedAt) > staleAfterMs) {
      console.warn(`Removing stale lock from ${lock.startedAt}`);
      unlinkSync(lockPath);
    }
  }

  let fd;
  try {
    fd = openSync(lockPath, 'wx');
  } catch (error) {
    if (error.code === 'EEXIST') {
      console.log('Another backfill/poll run is still active. Exiting without doing work.');
      process.exit(0);
    }
    throw error;
  }

  writeFileSync(
    fd,
    `${JSON.stringify({ pid: process.pid, startedAt: new Date().toISOString() }, null, 2)}\n`
  );
  closeSync(fd);

  const release = () => {
    try {
      unlinkSync(lockPath);
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  };

  process.once('exit', release);
  process.once('SIGINT', () => {
    release();
    process.exit(130);
  });
  process.once('SIGTERM', () => {
    release();
    process.exit(143);
  });

  return release;
}
