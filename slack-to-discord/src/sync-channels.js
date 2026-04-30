import { DiscordAdminClient, normalizeDiscordChannelName } from './discord.js';
import { syncConfig } from './config.js';
import { SlackClient } from './slack.js';
import { JsonStore } from './store.js';

const config = syncConfig();
const store = new JsonStore(config.dataDir);
const slack = new SlackClient(config.slackBotToken);
const discord = new DiscordAdminClient(config.discordBotToken);

const channels = await slack.listConversations(config.slackChannelTypes);
const guildChannels = await discord.getGuildChannels(config.discordGuildId);
const category = await ensureCategory(guildChannels);

let created = 0;
let updated = 0;
let skipped = 0;

for (const slackChannel of channels) {
  if (slackChannel.is_archived) {
    skipped += 1;
    continue;
  }

  if (slackChannel.is_channel && !slackChannel.is_member && config.autoJoinPublicChannels) {
    try {
      await slack.joinConversation(slackChannel.id);
    } catch (error) {
      console.warn(`Could not join #${slackChannel.name}: ${error.message}`);
    }
  }

  const existing = store.getChannelMapping(slackChannel.id);
  if (existing?.webhookUrl) {
    updated += 1;
    continue;
  }

  const discordName = normalizeDiscordChannelName(slackChannel.name, config.discordChannelPrefix);
  const textChannel = await ensureTextChannel(discordName, category.id, slackChannel);
  const webhook = await discord.createWebhook(textChannel.id, `Slack #${slackChannel.name}`);

  store.upsertChannelMapping(slackChannel.id, {
    slackChannelId: slackChannel.id,
    slackChannelName: slackChannel.name,
    slackIsPrivate: slackChannel.is_private,
    discordChannelId: textChannel.id,
    discordChannelName: textChannel.name,
    discordWebhookId: webhook.id,
    webhookUrl: discord.webhookUrl(webhook)
  });
  created += 1;
  console.log(`Mapped Slack #${slackChannel.name} -> Discord #${textChannel.name}`);
}

console.log(`Done. Created ${created}, already mapped ${updated}, skipped ${skipped}.`);

async function ensureCategory(guildChannels) {
  const existing = guildChannels.find(
    (channel) => channel.type === 4 && channel.name === config.discordCategoryName
  );
  if (existing) return existing;

  return discord.createGuildChannel(config.discordGuildId, {
    name: config.discordCategoryName,
    type: 4
  });
}

async function ensureTextChannel(name, categoryId, slackChannel) {
  const latestChannels = await discord.getGuildChannels(config.discordGuildId);
  const existing = latestChannels.find(
    (channel) => channel.type === 0 && channel.name === name && channel.parent_id === categoryId
  );
  if (existing) return existing;

  return discord.createGuildChannel(config.discordGuildId, {
    name,
    type: 0,
    parent_id: categoryId,
    topic: `Backup mirror for Slack #${slackChannel.name} (${slackChannel.id})`
  });
}
