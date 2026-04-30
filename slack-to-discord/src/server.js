import { createServer } from 'node:http';
import { DiscordWebhookClient } from './discord.js';
import { messagePayloads } from './format.js';
import { serverConfig } from './config.js';
import { SlackClient, verifySlackSignature } from './slack.js';
import { JsonStore } from './store.js';

const config = serverConfig();
const store = new JsonStore(config.dataDir);
const slack = new SlackClient(config.slackBotToken);
const discord = new DiscordWebhookClient();

const server = createServer(async (request, response) => {
  try {
    if (request.method === 'GET' && request.url === '/health') {
      json(response, 200, { ok: true });
      return;
    }

    if (request.method !== 'POST' || request.url !== '/slack/events') {
      json(response, 404, { ok: false, error: 'not_found' });
      return;
    }

    const rawBody = await readBody(request);
    const valid = verifySlackSignature({
      signingSecret: config.slackSigningSecret,
      timestamp: request.headers['x-slack-request-timestamp'],
      signature: request.headers['x-slack-signature'],
      rawBody
    });

    if (!valid) {
      json(response, 401, { ok: false, error: 'invalid_signature' });
      return;
    }

    const payload = JSON.parse(rawBody);
    if (payload.type === 'url_verification') {
      json(response, 200, { challenge: payload.challenge });
      return;
    }

    json(response, 200, { ok: true });

    if (payload.type === 'event_callback') {
      console.log(
        `Received Slack event ${payload.event_id}: ${payload.event?.type || 'unknown'} ${payload.event?.subtype || ''}`.trim()
      );
      setImmediate(() => {
        handleSlackEvent(payload).catch((error) => {
          console.error('Failed to handle Slack event', error);
        });
      });
    }
  } catch (error) {
    console.error(error);
    json(response, 500, { ok: false, error: 'internal_error' });
  }
});

server.listen(config.port, () => {
  const url = config.publicBaseUrl
    ? `${config.publicBaseUrl.replace(/\/$/, '')}/slack/events`
    : `http://localhost:${config.port}/slack/events`;
  console.log(`Slack to Discord backup listening on port ${config.port}`);
  console.log(`Slack Request URL: ${url}`);
});

async function handleSlackEvent(envelope) {
  if (!store.markEventProcessed(envelope.event_id)) {
    console.log(`Skipping duplicate Slack event ${envelope.event_id}`);
    return;
  }

  const event = envelope.event;
  if (event.type !== 'message') return;
  if (!config.includeBotMessages && event.subtype === 'bot_message') return;
  if (!config.includeMessageEdits && event.subtype === 'message_changed') return;
  if (!config.includeMessageDeletes && event.subtype === 'message_deleted') return;
  if (event.subtype && !['bot_message', 'file_share', 'message_changed', 'message_deleted'].includes(event.subtype)) {
    return;
  }

  const message = event.message || event.previous_message || event;
  const mapping = store.getChannelMapping(event.channel || message.channel);
  if (!mapping?.webhookUrl) {
    console.warn(`No Discord webhook mapping for Slack channel ${event.channel || message.channel}`);
    return;
  }

  const mode =
    event.subtype === 'message_changed'
      ? 'edited'
      : event.subtype === 'message_deleted'
        ? 'deleted'
        : 'message';

  const { discordFiles, fileNotes } = await prepareFiles(message.files || []);
  const payloads = await messagePayloads({
    event,
    slack,
    channelName: mapping.slackChannelName || event.channel,
    mode,
    fileNotes
  });

  for (let index = 0; index < payloads.length; index += 1) {
    await discord.execute(mapping.webhookUrl, payloads[index], index === 0 ? discordFiles : []);
  }
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

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

function json(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json' });
  response.end(JSON.stringify(body));
}
