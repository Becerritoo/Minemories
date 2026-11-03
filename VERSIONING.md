# Versionado de Minemories

Minemories utiliza [Semantic Versioning](https://semver.org/) y publica en
conjunto los módulos `common`, `client-common`, `fabric`, `forge`, `neoforge`,
`paper` y el paquete de recursos.

La versión se define una sola vez mediante `minemories_version` en
`gradle.properties`. Los artefactos cliente y Paper de una publicación deben
tener exactamente la misma versión base.

## Cuándo incrementar cada componente

- **PATCH** (`1.0.1`): correcciones compatibles que no cambian el protocolo ni
  requieren migraciones o acciones del administrador.
- **MINOR** (`1.1.0`): funciones nuevas compatibles con los clientes y datos de
  la misma versión mayor.
- **MAJOR** (`2.0.0`): cambios incompatibles en el protocolo, configuración,
  identidad firmada de objetos o formatos persistentes sin migración automática.

El entero `Protocol.VERSION` es independiente de SemVer, pero cualquier cambio
que haga incompatible a un cliente existente exige incrementar la versión mayor
del proyecto o mantener negociación y compatibilidad explícitas.

## Publicación

1. Actualizar `minemories_version` y `CHANGELOG.md`.
2. Ejecutar `./gradlew clean test release`.
3. Confirmar que Fabric, Forge y NeoForge se conectan a Paper con la misma versión del protocolo.
4. Crear un commit de publicación y la etiqueta firmada o anotada `vX.Y.Z`.
5. Subir la etiqueta. GitHub Actions validará que coincida con
   `minemories_version`, compilará y adjuntará JAR, paquete y sumas SHA-256.

No se deben reutilizar números de versión para artefactos con contenido o
protocolo diferentes.
