# Minemories

**English** | [Español](README.es.md)

[![CI](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml/badge.svg)](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/Becerritoo/Minemories)](https://github.com/Becerritoo/Minemories/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Capture your adventures. Share your memories.**

Minemories adds craftable cameras, film packs, collectible Instax-style map photographs, murals, client-side photo editing, and optional Discord sharing to Minecraft.

Paper works on its own with a server-side scene renderer. The optional Fabric, Forge, and NeoForge client mods capture the player's actual view and add previews, captions, local copies, and the Instax editor.

Current compatibility: **Minecraft Java 1.21.11**, Paper, Fabric, Forge, NeoForge, and Java 21.

## Highlights

- Craft cameras, load up to three 12-exposure film packs, and repair cameras in an anvil.
- Produce persistent 1×1 Instax maps that can be renamed, traded, duplicated, and framed.
- Enlarge client-rendered photographs into continuous 2×2, 3×3, or 4×4 murals.
- Capture and edit the real client view with Fabric, Forge, or NeoForge.
- Let unmodded and compatible Bedrock players use Paper's bounded scene renderer.
- Share through a protected server destination, DiscordSRV, or personal webhooks.
- Transfer server-managed photos through the existing Minecraft connection without another port.
- Validate sizes, sessions, images, cooldowns, permissions, and signed camera state on Paper.

## Downloads

- [Hangar](https://hangar.papermc.io/Becerritoo/Minemories) — Paper plugin.
- [Modrinth](https://modrinth.com/project/minemories) — Fabric, Forge, NeoForge, and Paper.
- [GitHub Releases](https://github.com/Becerritoo/Minemories/releases) — every artifact, resource pack, and SHA-256 checksums.

Install `minemories-paper-<version>.jar` in the server's `plugins/` directory. Players who want real-view capture install the JAR matching their loader in `mods/`; Fabric also requires Fabric API.

## Quick start

### Server owners

1. Install the Paper JAR and restart the server.
2. Configure DiscordSRV or a fallback webhook in `plugins/Minemories/config.yml`.
3. Run `/minemories reload` and `/minemories status`.
4. Publish the included resource pack for clients without a Minemories mod.

### Players

1. Craft a camera and a film pack, then load the pack by sneaking and right-clicking with it in the off hand.
2. Hold right-click to aim.
3. With a client mod, press left-click to capture; without one, release right-click for Paper rendering.
4. Keep the Instax, place it in a frame, or enlarge it in a cartography table.

## Documentation

Start with the [documentation index](docs/README.md), or open the [complete user and administrator guide](docs/GUIDE.md).

- [Installation and configuration](docs/GUIDE.md#installation)
- [Player experience, Instax prints, and murals](docs/GUIDE.md#player-experience)
- [DiscordSRV and AuthMe](docs/GUIDE.md#discordsrv-and-authme)
- [Commands and permissions](docs/GUIDE.md#commands-and-permissions)
- [Security and threat model](docs/SECURITY.md)
- [Binary protocol](docs/PROTOCOL.md)
- [Versioning policy](VERSIONING.md)
- [Contributing](CONTRIBUTING.md)

## Building

```bash
./gradlew clean test release
```

Release artifacts are written to `build/release/`.

Minemories is MIT licensed and developed by Becerritoo.
