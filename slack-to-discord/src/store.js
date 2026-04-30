import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

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

export class JsonStore {
  constructor(dataDir) {
    this.dataDir = resolve(process.cwd(), dataDir);
    mkdirSync(this.dataDir, { recursive: true });
    this.channelMapPath = join(this.dataDir, 'channel-map.json');
    this.processedEventsPath = join(this.dataDir, 'processed-events.json');
  }

  readChannelMap() {
    return readJson(this.channelMapPath, { channels: {} });
  }

  writeChannelMap(map) {
    writeJson(this.channelMapPath, map);
  }

  getChannelMapping(slackChannelId) {
    return this.readChannelMap().channels[slackChannelId];
  }

  upsertChannelMapping(slackChannelId, mapping) {
    const map = this.readChannelMap();
    map.channels[slackChannelId] = {
      ...map.channels[slackChannelId],
      ...mapping,
      updatedAt: new Date().toISOString()
    };
    this.writeChannelMap(map);
  }

  markEventProcessed(eventId, maxEntries = 5000) {
    const state = readJson(this.processedEventsPath, { events: [] });
    if (state.events.some((entry) => entry.id === eventId)) {
      return false;
    }

    state.events.push({ id: eventId, at: Date.now() });
    state.events.sort((a, b) => b.at - a.at);
    state.events = state.events.slice(0, maxEntries);
    writeJson(this.processedEventsPath, state);
    return true;
  }
}
