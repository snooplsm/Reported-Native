process.env.BACKFILL_MINUTES ||= '35';
process.env.BACKFILL_USE_STATE = process.env.BACKFILL_USE_STATE || 'false';
process.env.BACKFILL_DEDUPE = process.env.BACKFILL_DEDUPE || 'discord';
process.env.BACKFILL_DISCORD_SCAN_LIMIT ||= '100';
process.env.BACKFILL_LOCK ||= 'true';
process.env.BACKFILL_MAX_PAGES ||= '8';

await import('./backfill.js');
