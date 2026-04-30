import { existsSync } from 'node:fs';
import { syncConfig } from './config.js';
import { DiscordAdminClient } from './discord.js';
import { SlackClient } from './slack.js';
import { JsonStore } from './store.js';

const results = [];

function pass(message) {
  results.push({ ok: true, message });
}

function fail(message) {
  results.push({ ok: false, message });
}

function warn(message) {
  results.push({ ok: null, message });
}

try {
  if (existsSync('.env')) {
    pass('.env exists in this folder');
  } else {
    warn('.env is missing in this folder; this is okay only if you exported env vars in your shell');
  }

  const config = syncConfig();
  const store = new JsonStore(config.dataDir);
  const map = store.readChannelMap();
  const mappedCount = Object.keys(map.channels || {}).length;

  if (mappedCount > 0) pass(`${mappedCount} Discord webhook channel mappings exist`);
  else fail('No Discord webhook mappings found; run npm run sync:channels');

  const publicBaseUrl = process.env.PUBLIC_BASE_URL || '';
  if (!publicBaseUrl) {
    warn('PUBLIC_BASE_URL is not set; Slack still needs a public HTTPS Event Request URL');
  } else if (publicBaseUrl.includes('YOUR-PUBLIC-DOMAIN')) {
    fail('PUBLIC_BASE_URL still contains the placeholder domain');
  } else if (!publicBaseUrl.startsWith('https://')) {
    fail('PUBLIC_BASE_URL should be an HTTPS URL so Slack can call it');
  } else {
    pass(`PUBLIC_BASE_URL is set to ${publicBaseUrl}`);
    await checkPublicHealth(publicBaseUrl);
  }

  const slack = new SlackClient(config.slackBotToken);
  const auth = await slack.api('auth.test');
  pass(`Slack token works for workspace ${auth.team || auth.team_id}`);

  const discord = new DiscordAdminClient(config.discordBotToken);
  const channels = await discord.getGuildChannels(config.discordGuildId);
  pass(`Discord token can read ${channels.length} channels in guild ${config.discordGuildId}`);
} catch (error) {
  fail(error.message);
}

for (const result of results) {
  const marker = result.ok === true ? 'PASS' : result.ok === false ? 'FAIL' : 'WARN';
  console.log(`${marker}: ${result.message}`);
}

if (results.some((result) => result.ok === false)) {
  process.exitCode = 1;
}

async function checkPublicHealth(publicBaseUrl) {
  const url = `${publicBaseUrl.replace(/\/$/, '')}/health`;
  try {
    const response = await fetch(url);
    if (!response.ok) {
      fail(`Public health check failed at ${url}: HTTP ${response.status}`);
      return;
    }
    const json = await response.json();
    if (json.ok) pass(`Public health check works at ${url}`);
    else fail(`Public health check returned unexpected response at ${url}`);
  } catch (error) {
    fail(`Public health check could not reach ${url}: ${error.message}`);
  }
}
