# Protocolo Minemories 6

[English](PROTOCOL.md) | **Español**

Canal de plugin message: `minemories:control`.

Todos los enteros usan orden de bytes de red. Las cadenas son `int32 length`
seguido de UTF-8, con un máximo de 16 KiB. Ningún plugin message supera 32 KiB.

## Solicitud de ticket (cliente → Paper)

```text
u8 protocolVersion = 6
u8 opcode = 1
i64 requestId
```

Forma extendida para una cámara física:

```text
u8 protocolVersion = 6
u8 opcode = 1
i64 requestId
string captureToken
bool publishToDiscord
```

La forma corta se conserva para capturas sin cámara física.

## Respuesta de autorización (Paper → cliente)

```text
u8 protocolVersion = 6
u8 opcode = 2
i64 requestId
bool accepted
string message

si accepted:
  string token
  i64 expiresAtEpochMillis
  i32 maxBytes
  i32 targetBytes
  i32 maxWidth
  i32 maxHeight
  f32 jpegQuality
  i32 cooldownSeconds
  i32 maxCaptionCharacters
  bool coordinatesAllowed
  string serverName
```

## Inicio de transferencia (cliente → Paper)

```text
u8 protocolVersion = 6
u8 opcode = 3
i64 requestId
string token
i32 totalBytes
i32 chunkCount
string caption
bool includeCoordinates
i32 instaxLength
u8 instaxPixels[instaxLength]
i32 muralBytes
```

El ticket se consume en este punto. Paper comprueba jugador, permiso, bloqueo,
cooldown, tamaño y concurrencia antes de reservar `totalBytes`. Para una cámara
física, el mod cliente incluye exactamente 16 384 índices de la paleta de mapas y declara
262 144 bytes para la fuente mural 512×512 sin marco. En los demás flujos ambos valores
son cero. Paper valida el tamaño y cada índice antes de crear los mapas.

## Fragmento JPEG (cliente → Paper)

```text
u8 protocolVersion = 6
u8 opcode = 4
i64 requestId
i32 chunkIndex
i32 chunkLength
u8 data[chunkLength]
```

Cada fragmento contiene como máximo 24 KiB y debe llegar en orden. El flujo
concatena primero los `totalBytes` del JPEG y después `muralBytes` índices de
paleta. La suma debe coincidir exactamente con ambos tamaños declarados.

## Resultado (Paper → cliente)

```text
u8 protocolVersion = 6
u8 opcode = 5
i64 requestId
bool success
string message
```

## Activación de cámara (Paper → cliente)

```text
u8 protocolVersion = 6
u8 opcode = 6
string captureToken
i32 remainingDurability
i32 remainingExposures
```

## Transferencia de álbum heredada (sin uso)

```text
opcode 7: i64 transferId, i32 totalBytes, i32 chunkCount
opcode 8: i64 transferId, i32 chunkIndex, i32 chunkLength, u8 data[chunkLength]
```

Estos opcodes se conservan por compatibilidad de protocolo, pero Paper ya no los
emite. Las fotografías se entregan como mapas Instax y esos mapas son el álbum.

La transferencia no contiene UUID, nombre, mundo ni coordenadas proporcionadas
por el mod. Paper obtiene esos datos de la sesión y del ticket ligado al jugador.
