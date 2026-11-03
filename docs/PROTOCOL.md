# Minemories protocol 6

**English** | [Español](PROTOCOL.es.md)

Plugin-message channel: `minemories:control`.

All integers use network byte order. Strings are encoded as `int32 length` followed by UTF-8, with a maximum of 16 KiB. No plugin message exceeds 32 KiB.

## Ticket request (client → Paper)

```text
u8 protocolVersion = 6
u8 opcode = 1
i64 requestId
```

Extended form for a physical camera:

```text
u8 protocolVersion = 6
u8 opcode = 1
i64 requestId
string captureToken
bool publishToDiscord
```

The short form remains available for captures that do not use a physical camera.

## Authorization response (Paper → client)

```text
u8 protocolVersion = 6
u8 opcode = 2
i64 requestId
bool accepted
string message

if accepted:
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

## Transfer start (client → Paper)

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

The ticket is consumed at this point. Paper checks player, permission, ban, cooldown, size, and concurrency before allocating `totalBytes`. For a physical camera, the client mod includes exactly 16,384 map-palette indices and declares 262,144 bytes for the borderless 512×512 mural source. Both values are zero for other flows. Paper validates each length and palette index before creating maps.

## JPEG chunk (client → Paper)

```text
u8 protocolVersion = 6
u8 opcode = 4
i64 requestId
i32 chunkIndex
i32 chunkLength
u8 data[chunkLength]
```

Every chunk contains at most 24 KiB and must arrive in order. The stream concatenates the JPEG's `totalBytes` first and the mural's `muralBytes` palette indices afterward. The sum must exactly match both declared lengths.

## Result (Paper → client)

```text
u8 protocolVersion = 6
u8 opcode = 5
i64 requestId
bool success
string message
```

## Camera activation (Paper → client)

```text
u8 protocolVersion = 6
u8 opcode = 6
string captureToken
i32 remainingDurability
i32 remainingExposures
```

## Legacy album transfer (unused)

```text
opcode 7: i64 transferId, i32 totalBytes, i32 chunkCount
opcode 8: i64 transferId, i32 chunkIndex, i32 chunkLength, u8 data[chunkLength]
```

These opcodes remain for protocol compatibility, but Paper no longer emits them. Photos are delivered as Instax maps, and those maps form the album.

The transfer does not contain a UUID, player name, world, or coordinates supplied by the mod. Paper obtains those values from the authenticated session and player-bound ticket.
