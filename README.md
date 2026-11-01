# Minemories

[![CI](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml/badge.svg)](https://github.com/Becerritoo/Minemories/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/Becerritoo/Minemories)](https://github.com/Becerritoo/Minemories/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Captura tus aventuras. Comparte tus recuerdos.**

Minemories conecta una captura realizada dentro de Minecraft con uno o varios canales de Discord. En servidores compatibles usa el destino administrado por Paper sin entregar ese webhook al jugador; también permite destinos personales configurados localmente. El proyecto produce dos artefactos independientes:

- `minemories-fabric-<versión>.jar`: mod exclusivamente de cliente.
- `minemories-paper-<versión>.jar`: plugin de servidor y autoridad de seguridad.

Compatibilidad actual: **Minecraft Java 1.21.11**, Fabric y Paper, Java 21.

## Descargas

- [Modrinth](https://modrinth.com/project/minememories): versiones separadas para Fabric y Paper.
- [GitHub Releases](https://github.com/Becerritoo/Minemories/releases): JAR de cliente, JAR de servidor, paquete de recursos y sumas SHA-256.

Instala `minemories-fabric-<versión>.jar` en el cliente y
`minemories-paper-<versión>.jar` en el servidor. Cada artefacto se distribuye
por separado para evitar instalar un JAR en el entorno equivocado.

## Experiencia del jugador

En servidores con cámaras físicas activas, el jugador fabrica una cámara y carga
hasta tres rollos. Cada rollo admite 12 fotografías y la cámara tiene 64 puntos
de durabilidad. Mantener clic derecho levanta la cámara con la pose vanilla del
catalejo. Con Fabric, clic izquierdo dispara sin zoom ni marco de catalejo;
soltar el uso funciona como alternativa para clientes sin mod y Bedrock. El
obturador y el flash son visibles para quienes estén cerca. Con Fabric abre la
captura real del cliente; sin Fabric, Paper genera una fotografía de la escena
y la revela como un mapa estilo Instax. Agacharse al soltar cancela el disparo.

1. El jugador usa una cámara o pulsa `F2` para un destino personal.
2. Minemories captura el siguiente fotograma sin HUD si esa opción está activa.
3. Aparece una previsualización compacta; el mundo sigue visible alrededor mientras:
   - pie de foto opcional;
   - coordenadas opcionales;
   - copia local opcional;
   - opción persistente para ocultar el HUD en la siguiente captura.
4. Al pulsar **Enviar a Discord**, la ventana se cierra y la foto entra en la cola local. En modo automático usa Paper si está disponible; de lo contrario utiliza los webhooks personales activos.
5. Paper verifica sesión, permiso, bloqueo y, opcionalmente, AuthMe y DiscordSRV.
6. El plugin emite un ticket aleatorio, breve y de un solo uso.
7. El cliente divide el JPEG en fragmentos y los envía por la conexión existente de Minecraft; Paper los reensambla, valida, vuelve a codificar y publica mediante Discord. Para una cámara física, Fabric también prepara la impresión Instax de 128×128 y una fuente mural limpia de 512×512 usando índices de la paleta de mapas.

El engrane de la previsualización abre los ajustes avanzados sin llenar la interfaz fotográfica de controles. Desde ahí pueden agregarse hasta diez webhooks personales y elegirse cuatro modos: **Automático**, **Solo servidor**, **Solo personal** y **Servidor y personales**.

Agacharse y hacer clic derecho carga el rollo de la mano secundaria. Por ahora la
cámara admite varios rollos y usa primero el que fue cargado antes. Agacharse y
hacer clic izquierdo extrae el último rollo que todavía conserve exposiciones.
Cada disparo entrega una Instax; los mapas impresos forman el álbum del jugador.
Al usar la última exposición, el rollo se consume automáticamente y la cámara
continúa con el siguiente rollo cargado.

Las recetas de la cámara y del rollo se descubren automáticamente al entrar y
aparecen en el libro de recetas de la mesa de crafteo. La cámara incluye además
la palabra clave `camara` sin acento para facilitar su búsqueda.

Una fotografía Fabric puede ampliarse en la mesa de cartografía colocando la
Instax con exactamente 4, 9 o 16 papeles. El resultado entrega 4, 9 o 16 mapas
para un mural continuo 2×2, 3×3 o 4×4 sin el marco decorativo de la Instax. La
Instax original sí conserva su marco y se consume al ampliar.

Una Instax 1×1 se duplica colocando un mapa vacío en la mesa de cartografía. El
plugin crea una copia registrada con un mapa distinto, por lo que ambas copias
pueden ampliarse de manera independiente. Todas comparten una sola fuente
512×512; esta se elimina cuando se consume o destruye la última Instax original.
Las piezas de un mural pueden duplicarse con la mecánica vanilla, pero no pueden
volver a ampliarse.

En modo `AUTO`, los jugadores sin mod no reciben una previsualización: al soltar
la cámara se consume la exposición, se genera la escena con los chunks ya cargados,
entrega un mapa renombrable en yunque y, si está configurado, publica la imagen
sin pie de foto en Discord. Las impresiones conservan sus píxeles tras reiniciar
Paper y son independientes del rollo original.

Cuando Fabric está disponible, la Instax usa la captura real: recorta el centro,
reescala en color lineal con Lanczos hasta un área de 124×112, recupera detalles
con enfoque moderado y aplica tramado bidireccional con la paleta exacta de
mapas. Ese trabajo ocurre en la cola de procesamiento del cliente; Paper sólo
verifica los 16 384 índices y registra un mapa. El JPEG de Discord mantiene su
flujo y calidad actuales, incluida la revalidación del servidor.

La previsualización permite alternar entre la captura original y el resultado
exacto de la Instax. El botón directo **Editar Instax** abre su editor; también
puede accederse desde **Engrane → Edición Instax**. Ahí pueden elegirse los
presets **Suave**, **Equilibrado** y **Nítido**, o ajustar filtro, enfoque,
contraste, saturación y tramado con una vista en vivo. Los valores se guardan
localmente en `config/minemories.json` y no modifican el JPEG de Discord.

`Ctrl + F2` conserva la función de panorama de Minecraft. Fuera de una partida, `F2` mantiene el comportamiento normal.

## Seguridad

El cliente nunca recibe el webhook administrado por Paper y su UUID no forma parte de la solicitud. El plugin usa el UUID de la conexión Paper, por lo que funciona con servidores online y offline, AuthMe y FastLogin sin confiar en la identidad declarada por el mod.

Los controles importantes viven en el servidor:

- un solo ticket activo por jugador;
- ticket de un solo uso y expiración corta;
- el jugador debe seguir conectado al consumirlo;
- permiso `minemories.use` y lista de bloqueos persistente;
- tamaño total y cantidad de fragmentos comprobados antes de reservar memoria;
- fragmentos de 24 KiB estrictamente ordenados y una sola transferencia por jugador;
- solamente JPEG con firma y decodificación válidas;
- dimensiones y cantidad máxima de píxeles;
- reencodificación JPEG que elimina metadatos, contenido anexado y archivos políglotas;
- concurrencia limitada, cooldown individual y cuota global por minuto;
- pies de foto acotados y menciones de Discord desactivadas;
- coordenadas obtenidas por Paper, no aceptadas desde el cliente.
- UUID y revisión firmados mediante HMAC para cámaras y rollos;
- avance atómico de revisión al cargar rollos, disparar o reparar;
- bloqueo de copias duplicadas o metadatos alterados sin afectar el objeto vigente.

Un cliente modificado puede fabricar una imagen válida, porque ningún software cliente puede demostrar que sus píxeles proceden realmente del framebuffer. En el modo administrado por Paper sí queda impedido enviar documentos arbitrarios, cuerpos sin límite o utilizar el webhook del servidor fuera del flujo autorizado. Consulta [Modelo de amenazas](docs/SECURITY.md).

Los webhooks personales son una función separada: sus URLs se guardan sin ocultar en `config/minemories.json` y el mod envía directamente a Discord. Nunca se transmiten a Paper ni se escriben deliberadamente en el chat o los registros. Al no pasar por el plugin, tampoco están sujetos a permisos, bloqueos, DiscordSRV ni cuotas del servidor; el propietario del cliente es responsable de proteger esas URLs.

## Compilar

```bash
./gradlew clean check release
```

Los dos JAR finales aparecen en `build/release/`.

Para preparar un fork o enviar cambios, consulta [CONTRIBUTING.md](CONTRIBUTING.md).

## Instalación

### Paper

1. Copia el JAR Paper a `plugins/`.
2. Inicia una vez el servidor para crear `plugins/Minemories/config.yml`.
3. Si DiscordSRV está instalado, Minemories usa automáticamente su bot. Solo necesitas indicar un canal si no quieres usar el principal:

```yaml
discordsrv:
  # Vacío = canal principal de DiscordSRV
  channel-id: ""
  require-linked-account: false
```

El ID debe ser el ID numérico de un canal visible para el bot. Si DiscordSRV no está instalado, configura el webhook alternativo:

```yaml
server-name: "Mi servidor"

discord:
  webhook-url: "https://discord.com/api/webhooks/ID/TOKEN"
```

`limits.target-file-bytes` admite un número de bytes o tamaños legibles como
`200K`, `0.25M` y sus variantes `KB`, `MB`, `KiB` o `MiB`. Las unidades usan
múltiplos de 1024. El objetivo predeterminado es `200K` y puede configurarse entre
`64K` y `500K`. El techo de seguridad es siempre `500K`: está fijado en el plugin
y no puede aumentarse desde `config.yml`.

El cliente mantiene una cola local de hasta cinco fotografías y solicita cada
ticket solamente cuando le toca publicar. Después de una subida respeta el
cooldown indicado por Paper; Paper conserva la última palabra sobre límites y
cuotas aunque un cliente modificado ignore su cola.

4. Ejecuta `/minemories reload` y comprueba `/minemories status`. El estado muestra si la entrega utiliza el bot de DiscordSRV o el webhook.

No se abre ningún puerto adicional: fotografías, autorización y resultados viajan
por la misma conexión que el jugador ya mantiene con Minecraft.

### Cámara y rollos

Receta de la cámara (también se acepta reflejada):

```text
Oro          Cristal tintado    Oro
Cobre        Catalejo           Cobre
Redstone     Diamante           Polvo luminoso
```

La cámara se repara en un yunque con oro. Cada lingote devuelve 8 puntos y el
costo es `1 + cantidad de lingotes` niveles. Acepta `Irrompibilidad` y
`Reparación` mediante libros. Al llegar a cero no desaparece: cambia al modelo
roto, deja de disparar y conserva sus rollos.

Receta del rollo de 12 exposiciones:

```text
Mapa vacío   Papel             Mapa vacío
Tinta        Polvo luminoso    Tinta
Mapa vacío   Redstone          Mapa vacío
```

La receta utiliza cuatro mapas vacíos, un papel, dos sacos de tinta, un polvo
luminoso y una redstone.

```yaml
camera:
  enabled: true
  required-for-server: true
  capture-session-seconds: 900 # Tiempo disponible para revisar y editar
  durability: 64
  roll-slots: 3
  roll-capacity: 12
  repair-per-gold: 8
  capture-mode: AUTO # AUTO, CLIENT o SERVER
  server-renderer:
    enabled: true
    max-distance: 48
    max-queue: 4
    discord-upscale: 4
    render-entities: true
    publish-to-discord: true
    give-map: true
```

La sesión de captura dura 15 minutos de forma predeterminada y puede ajustarse
entre 60 y 7200 segundos. Es de un solo uso, queda ligada al jugador y a la
revisión exacta de la cámara, y se invalida al desconectarse.

El render de Paper representa bloques, iluminación aproximada y siluetas de
entidades; no puede reproducir shaders, paquetes de recursos ni partículas del
cliente. Se procesa de uno en uno fuera del hilo principal y nunca carga chunks
nuevos. `CLIENT` exige Fabric y `SERVER` fuerza esta representación incluso si
el jugador tiene el mod.

El plugin guarda miniaturas JPEG de aproximadamente 24 KiB en
`plugins/Minemories/cameras.db`; el PNG original continúa siendo local. Las
claves de firma viven en `plugins/Minemories/security.yml` y deben incluirse en
las copias de seguridad.

El JAR Fabric ya contiene los modelos. Para clientes sin Fabric se genera además
`minemories-resource-pack-<versión>.zip`, que debe publicarse como paquete de
recursos del servidor para mostrar las texturas personalizadas.

### Fabric

Cada jugador instala el JAR Fabric y Fabric API en su carpeta `mods/`. La primera ejecución crea `config/minemories.json`. El mod y el plugin deben usar la misma versión del protocolo.

## Versionado

Paper, Fabric, `common` y el paquete de recursos se publican juntos con una sola
versión SemVer. Un parche corrige errores compatibles; una versión menor agrega
funciones compatibles; una versión mayor puede romper el protocolo, formatos o
configuración. Consulta [VERSIONING.md](VERSIONING.md) antes de preparar una
publicación.

No hace falta configurar nada si el servidor tiene Minemories Paper: el modo **Automático** lo detecta y usa su destino. Si el servidor no tiene el plugin, abre el engrane en la previsualización, pulsa **Añadir webhook**, pega una URL oficial de Discord y guárdala. **Servidor y personales** publica la misma fotografía en ambos tipos de destino; los demás modos permiten restringir explícitamente el envío.

La copia local conserva el PNG original, que puede pesar varios megabytes. Para
la red, el cliente crea un JPEG de hasta 1920×1080, busca aproximadamente `200K`
y respeta el techo fijo de `500K`. Paper lo vuelve a codificar antes de Discord, por lo
que es normal que el archivo local y el publicado tengan pesos distintos.

Los destinos personales reutilizan ese único JPEG optimizado, comparten la misma cola y tienen su propio objetivo de tamaño y espera configurables desde la pantalla avanzada. La conexión va del cliente a Discord mediante HTTPS, por lo que tampoco requiere abrir puertos.

## DiscordSRV y AuthMe

Las integraciones son opcionales y se detectan en tiempo de ejecución:

```yaml
discordsrv:
  require-linked-account: true

authme:
  require-authenticated: true
```

Con `require-linked-account`, solamente se emite un ticket si DiscordSRV devuelve una cuenta vinculada para el UUID autorizado por Paper.

Cuando DiscordSRV está habilitado, Minemories reutiliza su sesión del bot para publicar el adjunto y el embed. `discordsrv.channel-id` selecciona un canal concreto; si queda vacío, se usa el canal principal configurado en `Channels` dentro de DiscordSRV. En este modo no hace falta un webhook de Minemories. El bot necesita permisos para ver el canal, enviar mensajes, adjuntar archivos e insertar enlaces.

Con AuthMe instalado, `require-authenticated` impide solicitar tickets antes del inicio de sesión. FastLogin no necesita una integración especial: se utiliza el UUID final que Paper asignó a la conexión.

## Comandos y permisos

| Comando | Función |
|---|---|
| `/minemories status` | Muestra transporte, transferencias, DiscordSRV y bloqueos. |
| `/minemories reload` | Recarga configuración y reinicia el servicio de transferencias. |
| `/minemories ban <jugador\|UUID>` | Impide nuevas fotografías para ese UUID. |
| `/minemories unban <jugador\|UUID>` | Retira el bloqueo. |
| `/minemories give <jugador> <camera\|rollo> [cantidad]` | Entrega objetos registrados para pruebas o administración. |

| Permiso | Predeterminado | Función |
|---|---:|---|
| `minemories.use` | todos | Solicitar y consumir tickets. |
| `minemories.admin` | OP | Administrar el plugin. |
| `minemories.bypass.cooldown` | OP | Ignorar únicamente el cooldown individual. |

## Ejemplo en Discord

El webhook publica un embed con la fotografía como adjunto:

```text
MikeUnuu
“Terminamos el faro después de tres días”

Servidor       Mi servidor
Ubicación      Mi servidor · 120, 64, -45
Discord        Vinculado

[fotografía]
Minemories • 23/10/2026
```

Las coordenadas y el UUID pueden ocultarse por configuración. El UUID está desactivado de forma predeterminada.

## Estructura

```text
minemories/
├── common/   Protocolo binario y pruebas
├── fabric/   Captura, previsualización, cola y transferencia fragmentada
├── paper/    Autorización, reensamblado, validación y Discord
└── docs/     Operación y seguridad
```

Licencia MIT. Desarrollado por Becerritoo.
