import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { backfillConfig } from './config.js';
import { DiscordAdminClient } from './discord.js';
import { JsonStore } from './store.js';

const config = backfillConfig();
if (!config.discordBotToken) {
  console.error('DISCORD_BOT_TOKEN is required to rebuild state from Discord.');
  process.exit(1);
}

const store = new JsonStore(config.dataDir);
const discord = new DiscordAdminClient(config.discordBotToken);
const statePath = resolve(process.cwd(), config.dataDir, 'backfill-state.json');
const state = readJson(statePath, { messages: {} });
const map = store.readChannelMap();
const mappings = Object.entries(map.channels || {})
  .map(([slackChannelId, mapping]) => ({ slackChannelId, ...mapping }))
  .filter((mapping) => mapping.discordChannelId)
  .filter(matchesChannelFilter);

let added = 0;

for (const mapping of mappings) {
  console.log(`Scanning Discord #${mapping.discordChannelName || mapping.discordChannelId}`);
  let before;
  let scanned = 0;

  while (true) {
    const messages = await discord.getChannelMessages(mapping.discordChannelId, 100, before);
    if (messages.length === 0) break;

    for (const message of messages) {
      scanned += 1;
      const slackTs = extractSlackTs(message);
      if (!slackTs) continue;

      const key = `${mapping.slackChannelId}:${slackTs}`;
      if (!state.messages[key]) {
        state.messages[key] = {
          channel: mapping.slackChannelName || mapping.slackChannelId,
          ts: slackTs,
          mirroredAt: message.timestamp,
          rebuiltFromDiscordMessageId: message.id
        };
        added += 1;
      }
    }

    before = messages[messages.length - 1].id;
  }

  console.log(`Scanned ${scanned} Discord message(s) for #${mapping.discordChannelName}`);
}

writeJson(statePath, state);
console.log(`Rebuilt ${statePath}. Added ${added} timestamp(s).`);

function extractSlackTs(message) {
  for (const embed of message.embeds || []) {
    const match = (embed.footer?.text || '').match(/\bts (\d+\.\d+)\b/);
    if (match) return match[1];
  }
  return null;
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
