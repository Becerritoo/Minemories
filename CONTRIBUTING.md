# Contribuir a Minemories

Gracias por mejorar Minemories. El repositorio está preparado para que un fork
pueda compilarse y ejecutar sus pruebas sin secretos ni servicios externos.

## Requisitos

- Git.
- JDK 21. No es necesario instalar Gradle; el wrapper está incluido.

## Preparar un fork

```bash
git clone https://github.com/TU_USUARIO/Minemories.git
cd Minemories
./gradlew clean test release
```

Los artefactos quedan en `build/release/`:

- `minemories-fabric-<versión>.jar`;
- `minemories-forge-<versión>.jar`;
- `minemories-neoforge-<versión>.jar`;
- `minemories-paper-<versión>.jar`;
- `minemories-resource-pack-<versión>.zip`.

El workflow de CI también ejecuta esta compilación en cada pull request. Los
forks no necesitan `MODRINTH_TOKEN`: la publicación en Modrinth está limitada al
repositorio oficial.

## Flujo de trabajo

1. Crea una rama desde `main`.
2. Mantén cada cambio enfocado y agrega pruebas cuando corresponda.
3. Ejecuta `./gradlew clean test release`.
4. Abre un pull request explicando el comportamiento anterior y el nuevo.

No incluyas mundos, registros, bases de datos, capturas privadas, configuraciones
de producción, webhooks, tokens ni `security.yml`. Los directorios de ejecución,
archivos de configuración local y formatos comunes de secretos están ignorados
por Git.

## Módulos

- `common`: protocolo y código compartido sin dependencias del loader.
- `client-common`: captura, interfaz, codificación, cola y mixins compartidos.
- `fabric`, `forge` y `neoforge`: arranque y transporte específicos de cada loader.
- `paper`: autoridad del servidor, cámaras, mapas y publicación en Discord.
- `resource-pack`: modelos y texturas para clientes sin mod.

Los cambios incompatibles del protocolo deben coordinar todos los clientes y Paper. Consulta
[VERSIONING.md](VERSIONING.md) y [docs/PROTOCOL.md](docs/PROTOCOL.md) antes de
modificar paquetes de red, datos persistentes o identidades firmadas.
