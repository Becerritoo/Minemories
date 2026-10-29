# Changelog

Este proyecto sigue [Semantic Versioning](https://semver.org/).

## [2.0.0] - 2026-10-29

- Protocolo 6 coordinado entre Fabric y Paper.
- El pie de foto se convierte en el nombre de la Instax impresa, con limpieza y límite seguro.
- Fuente mural 512×512 sin marco Instax generada y cuantizada por Fabric.
- Variantes temporales 256×256, 384×384 y 512×512 preparadas fuera del hilo principal.
- Ampliaciones 2×2, 3×3 y 4×4 con exactamente 4, 9 o 16 papeles.
- Murales continuos sin el borde decorativo de la Instax original.
- Piezas numeradas por fila y columna que heredan el título de la fotografía.
- Duplicación registrada de Instax 1×1 con un mapa vacío; cada copia conserva su propio derecho de ampliación.
- Fuente compartida mientras exista una Instax original y eliminación automática al consumirse la última.
- Las copias exactas obtenidas por exploits comparten el mismo registro y no crean derechos adicionales.

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

[2.0.0]: https://github.com/Becerritoo/Minemories/releases/tag/v2.0.0
[1.0.0]: https://github.com/Becerritoo/Minemories/releases/tag/v1.0.0
