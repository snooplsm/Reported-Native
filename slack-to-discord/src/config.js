import { boolEnv, loadEnv, numberEnv, optionalEnv, requiredEnv } from './env.js';

loadEnv();

export function serverConfig() {
  return {
    port: numberEnv('PORT', 3000),
    publicBaseUrl: optionalEnv('PUBLIC_BASE_URL', ''),
    dataDir: optionalEnv('DATA_DIR', './data'),
    slackBotToken: requiredEnv('SLACK_BOT_TOKEN'),
    slackSigningSecret: requiredEnv('SLACK_SIGNING_SECRET'),
    mirrorFiles: boolEnv('MIRROR_FILES', true),
    maxUploadBytes: numberEnv('DISCORD_MAX_UPLOAD_MB', 8) * 1024 * 1024,
    includeBotMessages: boolEnv('INCLUDE_BOT_MESSAGES', true),
    includeMessageEdits: boolEnv('INCLUDE_MESSAGE_EDITS', true),
    includeMessageDeletes: boolEnv('INCLUDE_MESSAGE_DELETES', true)
  };
}

export function syncConfig() {
  return {
    dataDir: optionalEnv('DATA_DIR', './data'),
    slackBotToken: requiredEnv('SLACK_BOT_TOKEN'),
    discordBotToken: requiredEnv('DISCORD_BOT_TOKEN'),
    discordGuildId: requiredEnv('DISCORD_GUILD_ID'),
    slackChannelTypes: optionalEnv('SLACK_CHANNEL_TYPES', 'public_channel,private_channel')
      .split(',')
      .map((type) => type.trim())
      .filter(Boolean),
    autoJoinPublicChannels: boolEnv('AUTO_JOIN_PUBLIC_CHANNELS', true),
    discordCategoryName: optionalEnv('DISCORD_CATEGORY_NAME', 'Slack Backup'),
    discordChannelPrefix: optionalEnv('DISCORD_CHANNEL_PREFIX', 'slack-')
  };
}

export function backfillConfig() {
  return {
    dataDir: optionalEnv('DATA_DIR', './data'),
    slackBotToken: requiredEnv('SLACK_BOT_TOKEN'),
    discordBotToken: optionalEnv('DISCORD_BOT_TOKEN', ''),
    mirrorFiles: boolEnv('MIRROR_FILES', true),
    maxUploadBytes: numberEnv('DISCORD_MAX_UPLOAD_MB', 8) * 1024 * 1024,
    includeBotMessages: boolEnv('INCLUDE_BOT_MESSAGES', true),
    days: numberEnv('BACKFILL_DAYS', 90),
    minutes: numberEnv('BACKFILL_MINUTES', 0),
    channelFilter: optionalEnv('BACKFILL_CHANNELS', '')
      .split(',')
      .map((channel) => channel.trim().replace(/^#/, '').toLowerCase())
      .filter(Boolean),
    pageLimit: numberEnv('BACKFILL_PAGE_LIMIT', 15),
    maxPages: numberEnv('BACKFILL_MAX_PAGES', 0),
    historyDelayMs: numberEnv('BACKFILL_HISTORY_DELAY_MS', 61_000),
    useState: boolEnv('BACKFILL_USE_STATE', true),
    dedupe: optionalEnv('BACKFILL_DEDUPE', 'state'),
    discordScanLimit: numberEnv('BACKFILL_DISCORD_SCAN_LIMIT', 100),
    lock: boolEnv('BACKFILL_LOCK', false),
    lockStaleMinutes: numberEnv('BACKFILL_LOCK_STALE_MINUTES', 240),
    includeThreads: boolEnv('BACKFILL_INCLUDE_THREADS', true),
    oldestTs: optionalEnv('BACKFILL_OLDEST_TS', ''),
    latestTs: optionalEnv('BACKFILL_LATEST_TS', '')
  };
}

export function pruneDeletedConfig() {
  return {
    dataDir: optionalEnv('DATA_DIR', './data'),
    slackBotToken: requiredEnv('SLACK_BOT_TOKEN'),
    discordBotToken: requiredEnv('DISCORD_BOT_TOKEN'),
    days: numberEnv('PRUNE_DAYS', 10),
    channelFilter: optionalEnv('PRUNE_CHANNELS', '')
      .split(',')
      .map((channel) => channel.trim().replace(/^#/, '').toLowerCase())
      .filter(Boolean),
    pageLimit: numberEnv('PRUNE_PAGE_LIMIT', 15),
    maxPages: numberEnv('PRUNE_MAX_PAGES', 0),
    historyDelayMs: numberEnv('PRUNE_HISTORY_DELAY_MS', 61_000),
    dryRun: boolEnv('PRUNE_DRY_RUN', false),
    lock: boolEnv('PRUNE_LOCK', true),
    lockStaleMinutes: numberEnv('PRUNE_LOCK_STALE_MINUTES', 240),
    includeThreads: boolEnv('PRUNE_INCLUDE_THREADS', true)
  };
}
