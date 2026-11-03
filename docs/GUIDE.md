# Minemories

**English** | [Español](GUIDE.es.md) | [Documentation index](README.md)

[![CI](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml/badge.svg)](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/Becerritoo/Minemories)](https://github.com/Becerritoo/Minemories/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)

**Capture your adventures. Share your memories.**

Minemories turns moments captured inside Minecraft into Instax-style map photos and can share them with one or more Discord channels. On compatible servers, it uses a Paper-managed destination without exposing the webhook to players. Players may also configure personal destinations locally.

The project ships four separate JARs:

- `minemories-fabric-<version>.jar`: client-side Fabric mod.
- `minemories-forge-<version>.jar`: client-side Forge mod.
- `minemories-neoforge-<version>.jar`: client-side NeoForge mod.
- `minemories-paper-<version>.jar`: server-side Paper plugin and security authority.

Current compatibility: **Minecraft Java 1.21.11**, Fabric, Forge, NeoForge and Paper, Java 21.

## Downloads

- [Modrinth](https://modrinth.com/project/minemories): separate Fabric, Forge, NeoForge and Paper versions.
- [GitHub Releases](https://github.com/Becerritoo/Minemories/releases): all client JARs, the server JAR, resource pack, and SHA-256 checksums.

Install the JAR matching the player's loader on the client and `minemories-paper-<version>.jar` on the server. The artifacts are distributed separately so the wrong JAR cannot accidentally be installed in the wrong environment.

## Player experience

On servers with physical cameras enabled, players craft a camera and load up to three film packs. Each pack supports 12 photos, and a camera starts with 64 durability points. Holding right click raises the camera using the vanilla spyglass pose. With a supported client mod, left click takes a photo without spyglass zoom or overlay. Releasing use is the fallback shutter action for unmodded and Bedrock players. Nearby players can see the flash and hear the shutter. Sneaking while releasing cancels the shot.

With any supported client mod, Minemories captures the player's actual view. Without a client mod, Paper renders the scene and develops it as an Instax-style map.

1. Use a camera, or press `F2` when taking a photo for a personal destination.
2. Minemories captures the next frame without the HUD when that option is enabled.
3. A compact preview keeps the world visible around it and provides:
   - an optional caption;
   - optional coordinates;
   - an optional local copy;
   - a persistent option to hide the HUD in the next capture.
4. Pressing **Send to Discord** closes the screen and adds the photo to the local queue. Automatic mode uses Paper when available and otherwise falls back to enabled personal webhooks.
5. Paper verifies the session, permission, ban status, and optionally AuthMe and DiscordSRV.
6. The plugin issues a short-lived, random, single-use upload ticket.
7. The client splits the JPEG into chunks and sends them through the existing Minecraft connection. Paper reassembles, validates, re-encodes, and publishes the image. For physical-camera shots, the client mod also creates the 128×128 Instax print and a clean 512×512 mural source using exact Minecraft map-palette indices.

The gear button opens advanced settings without cluttering the photo interface. Up to ten personal webhooks can be added, with four destination modes: **Automatic**, **Server only**, **Personal only**, and **Server and personal**.

Sneak and right click to load the film pack held in the off hand. Cameras currently support multiple packs and consume the oldest loaded pack first. Sneak and left click to remove the most recently loaded pack that still has exposures. Every shot creates an Instax print. A pack disappears automatically after its last exposure, and the camera continues with the next loaded pack.

Camera and film-pack recipes are discovered automatically when a player joins and appear in the crafting-table recipe book. The camera recipe also includes the Spanish search keyword `camara` without an accent.

A client-rendered Instax can be enlarged in a cartography table by combining it with exactly 4, 9, or 16 pieces of paper. The result is a set of 4, 9, or 16 maps for a continuous 2×2, 3×3, or 4×4 mural without the Instax border. The original Instax keeps its border and is consumed when enlarged.

Duplicate a 1×1 Instax by combining it with an empty map in a cartography table. The plugin creates an independently registered copy with a different map ID, allowing either copy to be enlarged. All originals share a single 512×512 source, which is removed after the final original Instax is enlarged or destroyed. Individual mural tiles can be duplicated with vanilla mechanics, but cannot be enlarged again.

In `AUTO` mode, players without the mod do not receive a preview. Releasing the camera consumes an exposure, renders the scene from already-loaded chunks, gives the player an anvil-renamable map, and optionally posts the image to Discord without a caption. Prints keep their pixels after Paper restarts and do not depend on the original film pack.

When Fabric, Forge or NeoForge is available, the Instax uses the real client capture. It crops the center, rescales in linear color with Lanczos to a 124×112 photo area, restores detail with moderate sharpening, and applies bidirectional dithering against the exact Minecraft map palette. This processing happens in the client queue; Paper only validates the 16,384 palette indices and registers the map. The Discord JPEG keeps its existing quality pipeline, including server-side revalidation.

The preview can switch between the original capture and the exact Instax result. The direct **Edit Instax** button opens a live editor, also available under **Gear → Instax editing**. Choose the **Soft**, **Balanced**, or **Sharp** preset, or adjust the filter, sharpness, contrast, saturation, and dithering manually. These values are stored locally in `config/minemories.json` and do not alter the Discord JPEG.

`Ctrl + F2` preserves Minecraft's panorama shortcut. Outside a world, `F2` keeps its normal vanilla behavior.

## Security

The client never receives the Paper-managed webhook, and its UUID is not part of the request. The plugin uses the UUID from the Paper connection, making the system compatible with online-mode and offline-mode servers, AuthMe, and FastLogin without trusting an identity declared by the mod.

The important controls live on the server:

- one active upload ticket per player;
- single-use tickets with short expiration;
- the player must remain connected when the ticket is consumed;
- `minemories.use` permission and a persistent ban list;
- total size and chunk count validation before allocating memory;
- strictly ordered 24 KiB chunks and one transfer per player;
- only valid, decodable JPEG input;
- maximum dimensions and pixel count;
- JPEG re-encoding to remove metadata, appended content, and polyglot files;
- bounded concurrency, individual cooldowns, and a global per-minute quota;
- bounded captions with Discord mentions disabled;
- coordinates obtained by Paper instead of accepted from the client;
- HMAC-signed UUIDs and revisions for cameras and film packs;
- atomic revision changes when loading packs, taking shots, or repairing;
- duplicate and tampered-item blocking without damaging the legitimate item.

A modified client can construct a valid image because client software cannot prove that its pixels came from the framebuffer. In Paper-managed mode, however, it cannot send arbitrary documents, unlimited bodies, or use the server webhook outside the authorized flow. See the [threat model](SECURITY.md).

Personal webhooks are a separate feature. Their URLs are stored in plain text in `config/minemories.json`, and the mod sends directly to Discord. They are never sent to Paper or deliberately written to chat or logs. Because they bypass the plugin, they are not governed by server permissions, bans, DiscordSRV linking, or quotas. The client owner is responsible for protecting those URLs.

## Building

```bash
./gradlew clean check release
```

All client JARs, the Paper JAR, and the resource pack are written to `build/release/`.

See [CONTRIBUTING.md](../CONTRIBUTING.md) to prepare a fork or submit a change.

## Installation

### Paper

1. Copy the Paper JAR into `plugins/`.
2. Start the server once to create `plugins/Minemories/config.yml`.
3. If DiscordSRV is installed, Minemories automatically uses its bot. Set a channel only when you do not want to use DiscordSRV's primary channel:

```yaml
discordsrv:
  # Empty = primary DiscordSRV channel
  channel-id: ""
  require-linked-account: false
```

The ID must identify a channel visible to the bot. If DiscordSRV is not installed, configure the fallback webhook:

```yaml
server-name: "My server"

discord:
  webhook-url: "https://discord.com/api/webhooks/ID/TOKEN"
```

`limits.target-file-bytes` accepts a raw byte count or human-readable sizes such as `200K`, `0.25M`, `KB`, `MB`, `KiB`, and `MiB`. Units use multiples of 1024. The default target is `200K` and can be configured from `64K` to `500K`. The hard safety ceiling is always `500K` and cannot be raised from `config.yml`.

The client keeps a local queue of up to five photos and only requests an upload ticket when each photo is ready to publish. After an upload, it respects Paper's cooldown. Paper remains authoritative for limits and quotas even if a modified client ignores its local queue.

4. Run `/minemories reload`, then check `/minemories status`. The status reports whether delivery uses DiscordSRV or the fallback webhook.

No additional port is required. Photos, authorization, and results use the same connection the player already maintains with Minecraft.

### Camera and film packs

Camera recipe, including its mirrored variant:

```text
Gold ingot    Tinted glass     Gold ingot
Copper ingot  Spyglass         Copper ingot
Redstone      Diamond          Glowstone dust
```

Repair the camera in an anvil with gold. Each ingot restores 8 durability points and costs `1 + number of ingots` experience levels. Cameras accept Unbreaking and Mending from enchanted books. At zero durability, the camera does not disappear: it changes to the broken model, stops taking photos, and keeps its loaded film packs.

Recipe for a 12-exposure film pack:

```text
Empty map     Paper            Empty map
Ink sac       Glowstone dust   Ink sac
Empty map     Redstone         Empty map
```

The recipe uses four empty maps, one paper, two ink sacs, one glowstone dust, and one redstone.

```yaml
camera:
  enabled: true
  required-for-server: true
  capture-session-seconds: 900 # Time available to review and edit
  durability: 64
  roll-slots: 3
  roll-capacity: 12
  repair-per-gold: 8
  capture-mode: AUTO # AUTO, CLIENT, or SERVER
  server-renderer:
    enabled: true
    max-distance: 48
    max-queue: 4
    discord-upscale: 4
    render-entities: true
    publish-to-discord: true
    give-map: true
```

Capture sessions last 15 minutes by default and can be configured between 60 and 7,200 seconds. A session is single-use, bound to the player and exact camera revision, and invalidated when the player disconnects.

Paper's renderer approximates blocks, lighting, and entity silhouettes. It cannot reproduce client shaders, resource packs, or particles. Scenes are processed one at a time outside the main thread, and Minemories never loads new chunks. `CLIENT` requires a supported client mod; `SERVER` forces Paper rendering even when the player has the mod.

The plugin stores JPEG thumbnails of approximately 24 KiB in `plugins/Minemories/cameras.db`; the original PNG remains local. Signing keys live in `plugins/Minemories/security.yml` and must be included in backups.

All client JARs already contain the item models. For clients without a mod, the build also produces `minemories-resource-pack-<version>.zip`. Publish it as the server resource pack to display the custom textures.

### Fabric, Forge and NeoForge

Each player installs the JAR matching their loader in the `mods/` directory. Fabric also requires Fabric API; Forge and NeoForge require no additional dependency. The first launch creates `config/minemories.json`. The mod and plugin must use compatible protocol versions.

No setup is required when a server runs Minemories Paper: **Automatic** mode detects it and uses its destination. When the server has no plugin, open the gear in the preview, press **Add webhook**, paste an official Discord webhook URL, and save it. **Server and personal** publishes the same photo to both destination types; the other modes explicitly restrict delivery.

The local copy keeps the original PNG, which may use several megabytes. For the network, the client creates a JPEG up to 1920×1080, targets approximately `200K`, and respects the fixed `500K` ceiling. Paper re-encodes it before Discord, so it is normal for the local and published files to have different sizes.

Personal destinations reuse the same optimized JPEG, share the local queue, and provide configurable size targets and cooldowns in advanced settings. The connection goes directly from the client to Discord over HTTPS and does not require opening a port.

## DiscordSRV and AuthMe

Integrations are optional and detected at runtime:

```yaml
discordsrv:
  require-linked-account: true

authme:
  require-authenticated: true
```

With `require-linked-account`, Paper only issues a ticket when DiscordSRV returns a linked Discord account for the UUID authorized by Paper.

When DiscordSRV is available, Minemories reuses its bot session to publish the attachment and embed. `discordsrv.channel-id` selects a specific channel; when empty, the primary channel configured under DiscordSRV's `Channels` section is used. A Minemories webhook is unnecessary in this mode. The bot needs permission to view the channel, send messages, attach files, and embed links.

With AuthMe installed, `require-authenticated` prevents ticket requests before login. FastLogin requires no special integration: Minemories uses the final UUID assigned to the Paper connection.

## Commands and permissions

| Command | Purpose |
|---|---|
| `/minemories status` | Shows delivery transport, transfers, DiscordSRV, and bans. |
| `/minemories reload` | Reloads configuration and restarts the transfer service. |
| `/minemories ban <player\|UUID>` | Blocks new photos for that UUID. |
| `/minemories unban <player\|UUID>` | Removes the block. |
| `/minemories give <player> <camera\|rollo> [amount]` | Gives registered items for testing or administration. |

| Permission | Default | Purpose |
|---|---:|---|
| `minemories.use` | everyone | Request and consume tickets. |
| `minemories.admin` | OP | Administer the plugin. |
| `minemories.bypass.cooldown` | OP | Bypass only the individual cooldown. |

## Discord example

The webhook posts an embed with the photo attached:

```text
MikeUnuu
“We finished the lighthouse after three days”

Server         My server
Location       My server · 120, 64, -45
Discord        Linked

[photo]
Minemories • 23/10/2026
```

Coordinates and UUID display can be disabled. UUID display is off by default.

## Project structure

```text
minemories/
├── common/         Binary protocol and tests
├── client-common/  Shared capture, UI, Instax editor, queue, and mixins
├── fabric/         Fabric bootstrap and transport
├── forge/          Forge bootstrap and transport
├── neoforge/       NeoForge bootstrap and transport
├── paper/          Authorization, reassembly, validation, and Discord
└── docs/           Operations and security
```

## Versioning

Paper, all three client loaders, `common`, and the resource pack are released together under one SemVer version. Patch releases contain compatible fixes, minor releases add compatible functionality, and major releases may change the protocol, formats, or configuration. See [VERSIONING.md](../VERSIONING.md) before preparing a release.

MIT licensed. Developed by Becerritoo.
