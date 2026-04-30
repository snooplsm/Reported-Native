import {
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  readFileSync,
  unlinkSync,
  writeFileSync
} from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pruneDeletedConfig } from './config.js';
import { DiscordAdminClient, DiscordWebhookClient } from './discord.js';
import { SlackClient, sleep } from './slack.js';
import { JsonStore } from './store.js';

const config = pruneDeletedConfig();
const store = new JsonStore(config.dataDir);
const slack = new SlackClient(config.slackBotToken);
const discordAdmin = new DiscordAdminClient(config.discordBotToken);
const discordWebhook = new DiscordWebhookClient();
const releaseLock = acquireLock();

const map = store.readChannelMap();
const mappings = Object.entries(map.channels || {})
  .map(([slackChannelId, mapping]) => ({ slackChannelId, ...mapping }))
  .filter((mapping) => mapping.webhookUrl && mapping.discordChannelId)
  .filter(matchesChannelFilter);

if (mappings.length === 0) {
  console.error('No mapped channels matched. Run npm run sync:channels first.');
  process.exit(1);
}

const latest = String(Math.floor(Date.now() / 1000));
const oldest = String(Math.floor(Date.now() / 1000) - config.days * 24 * 60 * 60);
const cutoffMs = Number(oldest) * 1000;
let deletedCount = 0;
let candidateCount = 0;

console.log(
  `Checking ${mappings.length} channel(s) for Slack deletions from the last ${config.days} day(s).`
);
if (config.dryRun) {
  console.log('Dry run enabled. No Discord messages will be deleted.');
}

for (const mapping of mappings) {
  await pruneChannel(mapping);
}

console.log(
  `Prune complete. Checked ${candidateCount} mirrored Discord message(s), deleted ${deletedCount}.`
);
releaseLock();

async function pruneChannel(mapping) {
  console.log(`Checking #${mapping.slackChannelName || mapping.slackChannelId}`);
  const slackMessages = await fetchSlackMessageTimestamps(mapping);
  const discordMessages = await fetchDiscordMirroredMessages(mapping);

  for (const message of discordMessages) {
    candidateCount += 1;
    if (slackMessages.has(message.slackTs)) continue;

    const label = `#${mapping.slackChannelName} Slack ts ${message.slackTs} -> Discord ${message.discordMessageId}`;
    if (config.dryRun) {
      console.log(`Would delete ${label}`);
      continue;
    }

    await discordWebhook.deleteMessage(mapping.webhookUrl, message.discordMessageId);
    deletedCount += 1;
    console.log(`Deleted ${label}`);
  }
}

async function fetchSlackMessageTimestamps(mapping) {
  const timestamps = new Set();
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
    for (const message of response.messages || []) {
      if (shouldCountSlackMessage(message)) {
        timestamps.add(message.ts);
      }
    }
    if (config.includeThreads) {
      for (const message of response.messages || []) {
        if (message.reply_count > 0) {
          await addThreadReplyTimestamps(mapping, message, timestamps);
        }
      }
    }

    cursor = response.response_metadata?.next_cursor || '';
    console.log(
      `Slack #${mapping.slackChannelName} page ${page}: ${response.messages?.length || 0} message(s)`
    );

    if (cursor) {
      if (config.maxPages > 0 && page >= config.maxPages) {
        console.warn(
          `Stopping #${mapping.slackChannelName} after ${page} page(s) because PRUNE_MAX_PAGES=${config.maxPages}`
        );
        break;
      }
      await sleep(config.historyDelayMs);
    }
  } while (cursor);

  return timestamps;
}

async function addThreadReplyTimestamps(mapping, parentMessage, timestamps) {
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
    for (const reply of response.messages || []) {
      if (reply.ts !== parentMessage.ts && shouldCountSlackMessage(reply)) {
        timestamps.add(reply.ts);
      }
    }

    cursor = response.response_metadata?.next_cursor || '';
    if (cursor) {
      if (config.maxPages > 0 && page >= config.maxPages) {
        console.warn(
          `Stopping thread ${parentMessage.ts} after ${page} page(s) because PRUNE_MAX_PAGES=${config.maxPages}`
        );
        break;
      }
      await sleep(config.historyDelayMs);
    }
  } while (cursor);
}

async function fetchDiscordMirroredMessages(mapping) {
  const mirrored = [];
  let before;

  while (true) {
    const messages = await discordAdmin.getChannelMessages(mapping.discordChannelId, 100, before);
    if (messages.length === 0) break;

    for (const message of messages) {
      const messageTime = Date.parse(message.timestamp);
      if (messageTime < cutoffMs) {
        return mirrored;
      }

      const slackTs = extractSlackTs(message);
      if (slackTs && Number(slackTs.split('.')[0]) >= Number(oldest)) {
        mirrored.push({
          discordMessageId: message.id,
          slackTs
        });
      }
    }

    before = messages[messages.length - 1].id;
  }

  return mirrored;
}

function extractSlackTs(message) {
  for (const embed of message.embeds || []) {
    const match = (embed.footer?.text || '').match(/\bts (\d+\.\d+)\b/);
    if (match) return match[1];
  }
  return null;
}

function shouldCountSlackMessage(message) {
  if (!message.subtype) return true;
  return ['bot_message', 'file_share', 'thread_broadcast'].includes(message.subtype);
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

function acquireLock() {
  if (!config.lock) return () => {};

  const lockPath = resolve(process.cwd(), config.dataDir, 'prune-deleted.lock');
  mkdirSync(dirname(lockPath), { recursive: true });

  if (existsSync(lockPath)) {
    const lock = readJson(lockPath, null);
    const staleAfterMs = config.lockStaleMinutes * 60 * 1000;
    if (lock?.startedAt && Date.now() - Date.parse(lock.startedAt) > staleAfterMs) {
      console.warn(`Removing stale prune lock from ${lock.startedAt}`);
      unlinkSync(lockPath);
    }
  }

  let fd;
  try {
    fd = openSync(lockPath, 'wx');
  } catch (error) {
    if (error.code === 'EEXIST') {
      console.log('Another prune run is still active. Exiting without doing work.');
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

function readJson(path, fallback) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') return fallback;
    throw error;
  }
}
