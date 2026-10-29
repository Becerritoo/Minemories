# Changelog

Este proyecto sigue [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-29

- Primera implementación Fabric y Paper para Minecraft 1.21.11.
- Captura mediante F2, modo HUD limpio y previsualización.
- Pie de foto, coordenadas opcionales y copia local.
- Tickets de subida ligados a la sesión Paper.
- Límites de cuerpo, dimensiones, píxeles, concurrencia, cooldown y cuota global.
- Validación y reencodificación de imágenes.
- Publicación por webhook con menciones desactivadas.
- Integraciones opcionales con AuthMe, FastLogin y DiscordSRV.
- Bloqueo persistente por UUID y comandos administrativos.
- Cámara y rollos crafteables con álbum transferible entre jugadores.
- Durabilidad inicial de 64, reparación en yunque con oro, Irrompibilidad y Reparación.
- Sonido de obturador, flash visible, modelos normal/roto y paquete de recursos.
- Miniaturas persistentes en SQLite y visor de álbum en Fabric.
- Identidades HMAC con revisiones atómicas para detectar copias duplicadas.
- Captura híbrida `AUTO`: framebuffer real con Fabric y render de perspectiva desde Paper sin mod.
- Impresiones estilo Instax en mapas persistentes, renombrables mediante yunque.
- Cola de un solo render activo, distancia acotada y uso exclusivo de chunks ya cargados.
- Publicación automática opcional en Discord para fotografías tomadas sin cliente Fabric.
- Generación de la Instax en Fabric con reescalado progresivo, tramado y paleta exacta de mapas.
- Transferencia de la impresión como 16 KiB de índices validados para evitar reescalado y cuantización en Paper.
- Instax de un solo mapa con reescalado Lanczos en color lineal, enfoque moderado y tramado bidireccional.
- Área fotográfica de 124×112 píxeles, conservando el mapa pixel art de 128×128 como fallback sin mod.
- Vista previa exacta Original/Instax y editor en vivo exclusivamente del lado Fabric.
- Presets Suave, Equilibrado y Nítido; filtro bicúbico/Lanczos y controles de enfoque, contraste, saturación y tramado.
- Ajustes Instax persistentes por cliente y captura inmutable de esos valores al entrar en la cola.
- Acceso directo `Editar Instax` desde la previsualización fotográfica.
- Apuntado continuo con pose vanilla de catalejo visible en tercera persona.
- Disparo Fabric con clic izquierdo, sin zoom ni marco; disparo alternativo al soltar y cancelación agachado.
- Migración automática de cámaras existentes a la nueva base de catalejo sin perder identidad ni desgaste.
- Protocolo 5 coordinado entre Fabric y Paper.
- Fuente Instax de 256×256 generada por Fabric para ampliaciones 2×2.
- Ampliación en mesa de cartografía: una fotografía y cuatro papeles entregan cuatro mapas distintos.
- Duplicación vanilla independiente de cada cuadrante en la mesa de cartografía.
- Rollos de 12 exposiciones fabricados con cuatro mapas vacíos.
- Selección de canal por ID al publicar mediante el bot de DiscordSRV.

[1.0.0]: https://github.com/Becerritoo/Minemories/releases/tag/v1.0.0
