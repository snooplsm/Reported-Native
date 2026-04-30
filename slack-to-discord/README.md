# Slack to Discord Realtime Backup

Mirrors Slack channel activity into Discord in near real time. It uses Slack Events API for new messages and Discord webhooks for delivery, with one webhook per mirrored Slack channel.

This is built for a Slack Free workspace: Slack currently limits visible message/file history to the most recent 90 days, and newer non-Marketplace Slack apps have tight `conversations.history` limits. The reliable approach is to start capturing new activity now rather than polling history.

## What It Backs Up

- Public Slack channel messages
- Private Slack channel messages where the Slack app has been invited
- Message edits and deletions as audit events
- File attachments, when Slack permits the bot to download them and the file fits Discord's upload limit
- Bot messages, unless disabled

It does not mirror DMs by default. Add the related Slack event scopes only if everyone in your workspace has consented to that.

## Setup

1. Create a Slack app at [api.slack.com/apps](https://api.slack.com/apps).
2. Import [`slack-app-manifest.yaml`](/Users/snooplsm/Reported-Native/slack-to-discord/slack-app-manifest.yaml) from **Create New App** -> **From an app manifest**. Replace `https://YOUR-PUBLIC-DOMAIN.example/slack/events` with your deployed event URL first, or update it later in **Event Subscriptions**.
3. If configuring manually instead, add these Slack bot token scopes:
   - `channels:read`
   - `channels:history`
   - `channels:join`
   - `groups:read`
   - `groups:history`
   - `users:read`
   - `files:read`
4. Under Slack **Event Subscriptions**, subscribe the bot to:
   - `message.channels`
   - `message.groups`
5. Install the Slack app to your workspace and copy the bot token plus signing secret.
6. Create a Discord bot in the [Discord Developer Portal](https://discord.com/developers/applications).
7. Invite it to your Discord server with permissions:
   - View Channels
   - Manage Channels
   - Manage Webhooks
   - Send Messages
   - Attach Files
   - Read Message History
8. Copy `.env.example` to `.env` and fill in the values.
9. Provision Discord channels and webhooks:

```bash
cd /Users/snooplsm/Reported-Native/slack-to-discord
npm run sync:channels
```

10. Start the server:

```bash
npm start
```

Or keep it running with Docker:

```bash
docker compose up -d --build
```

11. Expose the server publicly, for example with a reverse proxy or tunnel. Set Slack's Request URL to:

```text
https://your-public-domain.example/slack/events
```

The app also serves `GET /health`.

To check the local setup:

```bash
npm run doctor
```

## Backfill Visible Slack History

You can fetch older messages that Slack still exposes and post them into Discord:

```bash
npm run backfill
```

By default this tries the last 90 days for every mapped channel and records each mirrored Slack timestamp in `data/backfill-state.json`. That state file makes crash/retry safe: if the script stops halfway through, rerunning `npm run backfill` skips messages it already posted. Slack Free workspaces only expose recent history, and newer non-Marketplace Slack apps may only get 15 messages per `conversations.history` request with a one-request-per-minute rate limit, so a full workspace backfill can take a long time.

Thread replies are included when the parent thread message is inside the backfill/poll window. Polling cannot discover a new reply to an old thread unless the lookback window also includes that old parent message; use realtime mode for perfect thread capture.

Useful options:

```bash
BACKFILL_DAYS=14 npm run backfill
BACKFILL_CHANNELS=general,random npm run backfill
BACKFILL_PAGE_LIMIT=15 BACKFILL_HISTORY_DELAY_MS=61000 npm run backfill
```

For a safe scheduled job that does not use `backfill-state.json`, run the polling wrapper:

```bash
npm run poll
```

`npm run poll` checks the last 35 minutes of Slack history, scans the most recent Discord messages in each mapped channel, and skips Slack timestamps already present in Discord. It also uses `data/backfill.lock` so overlapping cron runs exit without doing work. This is the best mode for a cron job every 30 minutes:

```cron
*/30 * * * * cd /Users/snooplsm/Reported-Native/slack-to-discord && npm run poll >> poll.log 2>&1
```

This mode requires `DISCORD_BOT_TOKEN` because it reads recent Discord messages before posting. If a channel gets more than 100 mirrored messages inside the lookback window, increase the scan limit:

```bash
BACKFILL_DISCORD_SCAN_LIMIT=100 npm run poll
```

`npm run poll` also caps each channel at 8 Slack history pages by default. With Slack's 15-message page limit, that is up to 120 messages per channel per run. For very busy channels, you can raise or lower it:

```bash
BACKFILL_MAX_PAGES=4 npm run poll
BACKFILL_MAX_PAGES=0 npm run poll
```

For scheduled polling without state, use `npm run poll`. Full `npm run backfill` should keep state enabled.

State-file dedupe can be made explicit:

```bash
BACKFILL_USE_STATE=true BACKFILL_DEDUPE=state npm run backfill
```

If a previous backfill posted messages before state tracking was enabled, rebuild the state file from Discord before rerunning:

```bash
npm run state:rebuild
```

## Delete Discord Mirrors For Deleted Slack Messages

To remove mirrored Discord messages when the matching Slack message was deleted, run:

```bash
npm run prune:deleted
```

This checks the last 10 days by default. It reads mirrored Discord messages, extracts the Slack timestamp from the embed footer, fetches Slack's current visible history for the same channel and time window, then deletes Discord webhook messages whose Slack timestamp is missing from Slack.

Test it without deleting anything:

```bash
PRUNE_DRY_RUN=true npm run prune:deleted
```

Useful options:

```bash
PRUNE_DAYS=10 npm run prune:deleted
PRUNE_CHANNELS=general,random npm run prune:deleted
PRUNE_MAX_PAGES=8 npm run prune:deleted
```

Hourly cron:

```cron
0 * * * * cd /Users/snooplsm/Reported-Native/slack-to-discord && npm run prune:deleted >> prune-deleted.log 2>&1
```

The prune script does not query Slack for deleted messages directly, because Slack does not expose that as a history API. It infers deletes by comparing recent mirrored Discord messages against recent Slack history.

## Private Channels

Slack apps cannot read every private channel just because the installer is an admin. Invite the bot to each private channel that should be backed up, then rerun:

```bash
npm run sync:channels
```

## Discord Permission Troubleshooting

If `npm run sync:channels` fails with `Discord API failed 403` and `Missing Permissions`, reinvite the Discord bot with this permission set:

- View Channels
- Manage Channels
- Manage Webhooks
- Send Messages
- Attach Files
- Read Message History

The combined permissions integer for the OAuth2 invite URL is:

```text
536972304
```

You can also fix it inside Discord by opening **Server Settings** -> **Roles**, moving the bot's role above restricted backup/channel roles, and making sure no category permission override denies the bot `View Channel`, `Manage Channels`, or `Manage Webhooks`.

## Data Files

Runtime state is stored under `DATA_DIR`:

- `channel-map.json`: Slack channel IDs mapped to Discord webhook URLs.
- `processed-events.json`: recent Slack event IDs used to avoid duplicate retry posts.
- `backfill-state.json`: optional Slack message timestamp state when `BACKFILL_USE_STATE=true`.

Keep `DATA_DIR` private because webhook URLs can post into your Discord server.

## Notes

- Slack URL verification is handled automatically.
- Slack request signatures are verified before any event is accepted.
- Discord messages disable mentions, so a Slack `@here` or user mention will not notify Discord users.
- Deleted Slack messages are preserved as deletion audit entries in Discord rather than removed from the backup.
