# Minemories threat model

**English** | [Español](SECURITY.es.md)

## What is protected

- With the fallback transport, the server webhook URL exists only in `plugins/Minemories/config.yml` and is never sent to the client.
- With DiscordSRV, Minemories reuses its authenticated bot session and does not need to know or store the bot token.
- UUID, player name, and location come from the Paper session.
- Every ticket contains 256 random bits, expires quickly, and disappears after its first use attempt.
- A player's tickets are invalidated when that player disconnects.
- Only one ticket may remain pending for each UUID.
- The declared total size is checked against the fixed 500 KiB ceiling before the reassembly buffer is allocated.
- Every chunk is at most 24 KiB, must arrive in order, and must match the exact remaining size.
- Only one transfer per UUID is accepted, and global concurrency is bounded.
- JPEG signatures are checked before decoding.
- Dimensions are read with `ImageReader` before the complete image is constructed.
- The result is flattened to RGB and encoded again as JPEG before being sent to Discord.
- Every camera and film pack has an HMAC-SHA256-covered UUID, revision, and state.
- Paper stores the current revision in SQLite and advances it atomically for every mutation.
- An exact copy can act only while its revision remains current; afterward it becomes a blocked item.

## What cannot be guaranteed

The player controls the framebuffer and client process. A modified mod can fabricate pixels or capture offensive content. DiscordSRV linking, permissions, UUID bans, and human moderation remain necessary.

Minemories also cannot prove that a caption was written by an official client. Captions are treated as untrusted text, bounded, escaped for JSON, and prevented from creating Discord mentions.

Duplication cannot be detected while both copies remain unused in inventories or unloaded chunks. It is detected when an outdated copy attempts to load film, take a photo, repair, or otherwise mutate its state. Printed Instax maps are independent items and are not revoked.

## Recommended layers

1. Enable `discordsrv.require-linked-account: true` when the community already requires Discord linking.
2. Remove the `minemories.use` permission during sanctions.
3. Tune the cooldown and global quota for the community size.
4. Use a moderated Discord channel with appropriate retention.

Minemories does not listen on an additional socket. Images use the authenticated Minecraft session's plugin-message channel, and Paper never trusts a UUID declared inside transfer chunks.

## Personal destinations

Personal webhooks are outside Paper's trust model. They are deliberately stored unencrypted in `config/minemories.json`, and photos travel directly from the client to Discord over HTTPS. Their URLs are not sent to the server or intentionally shown in chat or logs.

A personal publication does not pass through server permissions, bans, quotas, AuthMe, or DiscordSRV. The official client limits queue size, image size, and frequency, but a modified client can ignore those restrictions because the webhook belongs to that player. If a personal URL leaks, delete or regenerate it in Discord.

## Incidents

To block a player immediately:

```text
/minemories ban <player or UUID>
```

The ban is stored in `plugins/Minemories/bans.yml`. Deleting or regenerating the webhook in Discord revokes future deliveries even if the server still stores the previous URL.

Never publish `config.yml` when it contains a real webhook. A DiscordSRV channel ID is not a secret, but the bot should have only the permissions it needs in that channel.

Do not publish `security.yml`. Losing it prevents validation of previously issued objects; leaking it allows signatures to be forged. Back it up together with `cameras.db`.
