# Minemories

[English](README.md) | **Español**

[![CI](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml/badge.svg)](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/Becerritoo/Minemories)](https://github.com/Becerritoo/Minemories/releases)
[![Licencia: MIT](https://img.shields.io/badge/licencia-MIT-blue.svg)](LICENSE)

**Captura tus aventuras. Comparte tus recuerdos.**

Minemories agrega cámaras crafteables, rollos, fotografías coleccionables estilo Instax, murales, edición fotográfica en el cliente y publicación opcional en Discord.

Paper funciona por sí solo mediante un renderizador de escenas del servidor. Los mods opcionales para Fabric, Forge y NeoForge capturan la vista real del jugador y agregan previsualización, pie de foto, copia local y editor Instax.

Compatibilidad actual: **Minecraft Java 1.21.11**, Paper, Fabric, Forge, NeoForge y Java 21.

## Características principales

- Craftea cámaras, carga hasta tres rollos de 12 exposiciones y repara cámaras en un yunque.
- Produce mapas Instax persistentes 1×1 que pueden renombrarse, intercambiarse, duplicarse y enmarcarse.
- Amplía fotografías del cliente en murales continuos 2×2, 3×3 o 4×4.
- Captura y edita la vista real con Fabric, Forge o NeoForge.
- Permite que clientes sin mod y jugadores Bedrock compatibles utilicen el renderizador acotado de Paper.
- Publica mediante un destino protegido del servidor, DiscordSRV o webhooks personales.
- Transfiere fotografías administradas por el servidor mediante la conexión existente de Minecraft, sin abrir otro puerto.
- Valida tamaños, sesiones, imágenes, cooldowns, permisos y el estado firmado de cámaras desde Paper.

## Descargas

- [Hangar](https://hangar.papermc.io/Becerritoo/Minemories) — plugin para Paper.
- [Modrinth](https://modrinth.com/project/minemories) — Fabric, Forge, NeoForge y Paper.
- [GitHub Releases](https://github.com/Becerritoo/Minemories/releases) — todos los artefactos, paquete de recursos y sumas SHA-256.

Instala `minemories-paper-<versión>.jar` en `plugins/` del servidor. Quien quiera captura de la vista real instala en `mods/` el JAR correspondiente a su loader; Fabric también requiere Fabric API.

## Inicio rápido

### Administradores

1. Instala el JAR Paper y reinicia el servidor.
2. Configura DiscordSRV o un webhook alternativo en `plugins/Minemories/config.yml`.
3. Ejecuta `/minemories reload` y `/minemories status`.
4. Publica el paquete de recursos incluido para clientes sin un mod de Minemories.

### Jugadores

1. Craftea una cámara y un rollo; carga el rollo agachándote y haciendo clic derecho con él en la mano secundaria.
2. Mantén clic derecho para apuntar.
3. Con un mod cliente, pulsa clic izquierdo; sin mod, suelta clic derecho para usar el render de Paper.
4. Conserva la Instax, colócala en un marco o amplíala en una mesa de cartografía.

## Documentación

Comienza con el [índice de documentación](docs/README.es.md) o abre la [guía completa para jugadores y administradores](docs/GUIDE.es.md).

- [Instalación y configuración](docs/GUIDE.es.md#instalación)
- [Experiencia, impresiones Instax y murales](docs/GUIDE.es.md#experiencia-del-jugador)
- [DiscordSRV y AuthMe](docs/GUIDE.es.md#discordsrv-y-authme)
- [Comandos y permisos](docs/GUIDE.es.md#comandos-y-permisos)
- [Seguridad y modelo de amenazas](docs/SECURITY.es.md)
- [Protocolo binario](docs/PROTOCOL.es.md)
- [Política de versionado](VERSIONING.md)
- [Contribuir](CONTRIBUTING.md)

## Compilar

```bash
./gradlew clean test release
```

Los artefactos se generan en `build/release/`.

Minemories tiene licencia MIT y es desarrollado por Becerritoo.
