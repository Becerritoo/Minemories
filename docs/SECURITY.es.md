# Modelo de amenazas de Minemories

[English](SECURITY.md) | **Español**

## Lo que se protege

- Cuando se usa el transporte alternativo, la URL del webhook del servidor solamente existe en `plugins/Minemories/config.yml` y nunca se entrega al cliente.
- Con DiscordSRV, Minemories reutiliza la sesión autenticada de su bot y no necesita conocer ni almacenar su token.
- El UUID, nombre y ubicación proceden de la sesión Paper.
- Cada ticket contiene 256 bits aleatorios, caduca y desaparece al primer intento de uso.
- Al salir del servidor se invalidan los tickets del jugador.
- Solamente existe un ticket pendiente por UUID para evitar acumulación deliberada.
- El tamaño total se valida contra el techo fijo de 500 KiB antes de reservar el búfer de reensamblado.
- Cada fragmento tiene como máximo 24 KiB, debe llegar en orden y coincidir exactamente con el tamaño restante.
- Solo se admite una transferencia por UUID y la concurrencia global permanece acotada.
- Antes de decodificar se comprueba la firma JPEG.
- Se consultan dimensiones con `ImageReader` antes de construir la imagen completa.
- El resultado se aplana a RGB y se vuelve a codificar como JPEG antes de salir hacia Discord.
- Cada cámara y rollo tiene UUID, revisión y estado cubiertos por HMAC-SHA256.
- Paper conserva la revisión vigente en SQLite y la avanza atómicamente durante cada mutación.
- Una copia exacta solo puede actuar mientras su revisión siga vigente; después se convierte en un objeto bloqueado.

## Lo que no puede garantizarse

Un cliente controla su framebuffer y su proceso. Puede modificar el mod, fabricar píxeles o fotografiar contenido ofensivo. La vinculación con DiscordSRV, los permisos, el bloqueo por UUID y la moderación siguen siendo necesarios.

Tampoco se intenta demostrar que el pie de foto fue escrito por una versión oficial del mod. Se trata como texto no confiable, se limita, escapa para JSON y no puede generar menciones.

La duplicación no puede detectarse mientras ambas copias permanezcan sin usar en
inventarios o chunks descargados. Se detecta al intentar cargar, disparar,
reparar o modificar una copia con revisión antigua. Los mapas Instax ya impresos
son objetos independientes y no se revocan.

## Capas recomendadas

1. `discordsrv.require-linked-account: true` cuando la comunidad ya exige vinculación.
2. Permiso `minemories.use` retirado durante sanciones.
3. Cooldown y cuota global adaptados al tamaño de la comunidad.
4. Canal de Discord con moderadores y retención apropiada.

Minemories no escucha en sockets adicionales. La imagen usa el canal de plugin
message de la sesión Minecraft autenticada y Paper nunca confía en un UUID
declarado dentro de los fragmentos.

## Destinos personales

Los webhooks personales no forman parte del modelo de confianza de Paper. Se
guardan sin cifrar en `config/minemories.json` por decisión del usuario y las
fotografías viajan directamente del cliente a Discord mediante HTTPS. Sus URLs no
se envían al servidor ni se muestran deliberadamente en chat o registros.

Una publicación personal no pasa por permisos, bloqueos, cuotas, AuthMe ni
DiscordSRV. El cliente oficial limita la cola, el tamaño y la frecuencia, pero un
cliente modificado puede ignorar esas restricciones porque el webhook pertenece
al propio jugador. Si una URL personal se filtra, debe eliminarse o regenerarse
desde Discord.

## Incidentes

Para bloquear inmediatamente a un jugador:

```text
/minemories ban <jugador o UUID>
```

El bloqueo queda en `plugins/Minemories/bans.yml`. Cambiar o eliminar el webhook desde Discord revoca cualquier entrega futura incluso si el servidor todavía conserva la URL anterior.

Nunca publiques `config.yml` si contiene un webhook real. El ID de canal de
DiscordSRV no es un secreto, pero el bot debe tener únicamente los permisos que
necesita en ese canal.

`security.yml` tampoco debe publicarse. Perderlo impide validar los objetos ya
emitidos; copiarlo fuera del servidor permite fabricar firmas. Debe respaldarse
junto con `cameras.db`.
