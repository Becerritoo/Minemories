package dev.becerritoo.minemories.paper;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class CameraStore implements AutoCloseable {
    static final String ACTIVE = "ACTIVE";
    static final String WORN = "WORN";
    private final String jdbcUrl;

    CameraStore(File dataFolder) {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta de Minemories");
        }
        jdbcUrl = "jdbc:sqlite:" + new File(dataFolder, "cameras.db").getAbsolutePath();
        initialize();
    }

    private void initialize() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cameras (
                      id TEXT PRIMARY KEY,
                      revision INTEGER NOT NULL,
                      state TEXT NOT NULL,
                      damage INTEGER NOT NULL,
                      shutter_count INTEGER NOT NULL,
                      max_damage INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rolls (
                      id TEXT PRIMARY KEY,
                      revision INTEGER NOT NULL,
                      state TEXT NOT NULL,
                      exposures INTEGER NOT NULL,
                      capacity INTEGER NOT NULL,
                      camera_id TEXT,
                      position INTEGER,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS rolls_camera ON rolls(camera_id, position)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS photos (
                      id TEXT PRIMARY KEY,
                      roll_id TEXT NOT NULL,
                      slot INTEGER NOT NULL,
                      player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL,
                      created_at INTEGER NOT NULL,
                      caption TEXT NOT NULL DEFAULT '',
                      image BLOB,
                      UNIQUE(roll_id, slot)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS photos_roll ON photos(roll_id, slot)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS prints (
                      map_id INTEGER PRIMARY KEY,
                      photo_id TEXT NOT NULL,
                      pixels BLOB NOT NULL,
                      grid_size INTEGER NOT NULL DEFAULT 1,
                      tile_x INTEGER NOT NULL DEFAULT 0,
                      tile_y INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL
                    )
                    """);
            ensureColumn(connection, "prints", "grid_size", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "prints", "tile_x", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "prints", "tile_y", "INTEGER NOT NULL DEFAULT 0");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS photo_renders (
                      photo_id TEXT NOT NULL,
                      grid_size INTEGER NOT NULL,
                      pixels BLOB NOT NULL,
                      created_at INTEGER NOT NULL,
                      PRIMARY KEY(photo_id, grid_size)
                    )
                    """);
            // Desde la mecánica Instax, un cartucho se consume al usar su última exposición.
            // Los registros fotográficos antiguos se conservan para no afectar mapas ya impresos.
            statement.executeUpdate("DELETE FROM rolls WHERE camera_id IS NOT NULL AND exposures>=capacity");
        } catch (SQLException exception) {
            throw failure("No se pudo abrir cameras.db", exception);
        }
    }

    synchronized CameraRecord createCamera(int maxDamage) {
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cameras(id,revision,state,damage,shutter_count,max_damage,created_at,updated_at) "
                        + "VALUES(?,1,?,0,0,?,?,?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, ACTIVE);
            statement.setInt(3, maxDamage);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
            return new CameraRecord(id, 1L, ACTIVE, 0, 0, maxDamage);
        } catch (SQLException exception) {
            throw failure("No se pudo crear la cámara", exception);
        }
    }

    synchronized RollRecord createRoll(int capacity) {
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rolls(id,revision,state,exposures,capacity,camera_id,position,created_at,updated_at) "
                        + "VALUES(?,1,?,0,?,NULL,NULL,?,?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, ACTIVE);
            statement.setInt(3, capacity);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
            return new RollRecord(id, 1L, ACTIVE, 0, capacity, null, null);
        } catch (SQLException exception) {
            throw failure("No se pudo crear el rollo", exception);
        }
    }

    synchronized Optional<CameraRecord> camera(UUID id) {
        try (Connection connection = connect()) {
            return Optional.ofNullable(readCamera(connection, id));
        } catch (SQLException exception) {
            throw failure("No se pudo consultar la cámara", exception);
        }
    }

    synchronized Optional<RollRecord> roll(UUID id) {
        try (Connection connection = connect()) {
            return Optional.ofNullable(readRoll(connection, id));
        } catch (SQLException exception) {
            throw failure("No se pudo consultar el rollo", exception);
        }
    }

    synchronized Attachment attach(UUID cameraId, long cameraRevision, UUID rollId, long rollRevision,
                                   int maximumRolls) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                CameraRecord camera = readCamera(connection, cameraId);
                RollRecord roll = readRoll(connection, rollId);
                if (camera == null || roll == null) {
                    return rollback(connection, Attachment.failure("El objeto ya no existe."));
                }
                if (camera.revision() != cameraRevision || roll.revision() != rollRevision) {
                    return rollback(connection, Attachment.failure("Se detectó una copia desactualizada."));
                }
                if (!ACTIVE.equals(roll.state()) || roll.cameraId() != null) {
                    return rollback(connection, Attachment.failure("Ese rollo ya está cargado en otra cámara."));
                }
                if (roll.exposures() >= roll.capacity()) {
                    return rollback(connection, Attachment.failure("Ese rollo ya está agotado."));
                }
                int count;
                int position;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*), COALESCE(MAX(position),-1)+1 FROM rolls WHERE camera_id=?")) {
                    statement.setString(1, cameraId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        count = result.getInt(1);
                        position = result.getInt(2);
                    }
                }
                if (count >= maximumRolls) {
                    return rollback(connection, Attachment.failure("La cámara ya tiene " + maximumRolls + " rollos."));
                }
                long now = System.currentTimeMillis();
                update(connection, "UPDATE cameras SET revision=revision+1,updated_at=? WHERE id=? AND revision=?",
                        now, cameraId.toString(), cameraRevision);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rolls SET revision=revision+1,camera_id=?,position=?,updated_at=? WHERE id=? AND revision=?")) {
                    statement.setString(1, cameraId.toString());
                    statement.setInt(2, position);
                    statement.setLong(3, now);
                    statement.setString(4, rollId.toString());
                    statement.setLong(5, rollRevision);
                    requireOne(statement.executeUpdate());
                }
                connection.commit();
                return Attachment.success(readCamera(connection, cameraId), readRoll(connection, rollId));
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo cargar el rollo", exception);
        }
    }

    synchronized Attachment detach(UUID cameraId, long cameraRevision) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                CameraRecord camera = readCamera(connection, cameraId);
                if (camera == null) {
                    return rollback(connection, Attachment.failure("La cámara ya no existe."));
                }
                if (camera.revision() != cameraRevision) {
                    return rollback(connection, Attachment.failure("Se detectó una copia desactualizada."));
                }
                RollRecord selected = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM rolls WHERE camera_id=? AND state=? AND exposures<capacity "
                                + "ORDER BY position DESC LIMIT 1")) {
                    statement.setString(1, cameraId.toString());
                    statement.setString(2, ACTIVE);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            selected = mapRoll(result);
                        }
                    }
                }
                if (selected == null) {
                    return rollback(connection, Attachment.failure(
                            "La cámara no contiene rollos con exposiciones disponibles."));
                }
                long now = System.currentTimeMillis();
                update(connection, "UPDATE cameras SET revision=revision+1,updated_at=? WHERE id=? AND revision=?",
                        now, cameraId.toString(), cameraRevision);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rolls SET revision=revision+1,camera_id=NULL,position=NULL,updated_at=? "
                                + "WHERE id=? AND revision=?")) {
                    statement.setLong(1, now);
                    statement.setString(2, selected.id().toString());
                    statement.setLong(3, selected.revision());
                    requireOne(statement.executeUpdate());
                }
                connection.commit();
                return Attachment.success(readCamera(connection, cameraId), readRoll(connection, selected.id()));
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo extraer el rollo", exception);
        }
    }

    synchronized ShotResult reserveShot(UUID cameraId, long expectedRevision, boolean consumeDurability) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                CameraRecord camera = readCamera(connection, cameraId);
                if (camera == null) {
                    return rollback(connection, ShotResult.failure(ShotStatus.UNKNOWN, "La cámara ya no existe."));
                }
                if (camera.revision() != expectedRevision) {
                    return rollback(connection, ShotResult.failure(ShotStatus.DUPLICATE,
                            "Esta copia de la cámara está desactualizada."));
                }
                if (camera.damage() >= camera.maxDamage() || WORN.equals(camera.state())) {
                    return rollback(connection, ShotResult.failure(ShotStatus.WORN, "La cámara está rota."));
                }
                RollRecord roll = firstAvailableRoll(connection, cameraId);
                if (roll == null) {
                    return rollback(connection, ShotResult.failure(ShotStatus.NO_FILM,
                            "La cámara no tiene un rollo con exposiciones disponibles."));
                }
                int damage = Math.min(camera.maxDamage(), camera.damage() + (consumeDurability ? 1 : 0));
                String state = damage >= camera.maxDamage() ? WORN : ACTIVE;
                long now = System.currentTimeMillis();
                UUID photoId = UUID.randomUUID();
                int usedExposures = roll.exposures() + 1;
                boolean rollConsumed = usedExposures >= roll.capacity();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cameras SET revision=revision+1,state=?,damage=?,shutter_count=shutter_count+1,updated_at=? "
                                + "WHERE id=? AND revision=?")) {
                    statement.setString(1, state);
                    statement.setInt(2, damage);
                    statement.setLong(3, now);
                    statement.setString(4, cameraId.toString());
                    statement.setLong(5, expectedRevision);
                    requireOne(statement.executeUpdate());
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rolls SET revision=revision+1,exposures=exposures+1,updated_at=? "
                                + "WHERE id=? AND revision=? AND exposures<capacity")) {
                    statement.setLong(1, now);
                    statement.setString(2, roll.id().toString());
                    statement.setLong(3, roll.revision());
                    requireOne(statement.executeUpdate());
                }
                if (rollConsumed) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM rolls WHERE id=? AND revision=?")) {
                        statement.setString(1, roll.id().toString());
                        statement.setLong(2, roll.revision() + 1);
                        requireOne(statement.executeUpdate());
                    }
                }
                connection.commit();
                RollRecord updatedRoll = new RollRecord(roll.id(), roll.revision() + 1, roll.state(),
                        usedExposures, roll.capacity(), roll.cameraId(), roll.position());
                return ShotResult.success(photoId, readCamera(connection, cameraId), updatedRoll, rollConsumed);
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo registrar la fotografía", exception);
        }
    }

    synchronized Optional<CameraRecord> repair(UUID cameraId, long expectedRevision, int newDamage) {
        try (Connection connection = connect()) {
            CameraRecord camera = readCamera(connection, cameraId);
            if (camera == null || camera.revision() != expectedRevision) {
                return Optional.empty();
            }
            int damage = Math.max(0, Math.min(camera.maxDamage(), newDamage));
            String state = damage >= camera.maxDamage() ? WORN : ACTIVE;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cameras SET revision=revision+1,state=?,damage=?,updated_at=? WHERE id=? AND revision=?")) {
                statement.setString(1, state);
                statement.setInt(2, damage);
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, cameraId.toString());
                statement.setLong(5, expectedRevision);
                if (statement.executeUpdate() != 1) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(readCamera(connection, cameraId));
        } catch (SQLException exception) {
            throw failure("No se pudo reparar la cámara", exception);
        }
    }

    synchronized void finishPhoto(UUID photoId, String caption, byte[] thumbnail) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE photos SET caption=?,image=? WHERE id=? AND image IS NULL")) {
            statement.setString(1, caption == null ? "" : caption);
            statement.setBytes(2, thumbnail);
            statement.setString(3, photoId.toString());
            requireOne(statement.executeUpdate());
        } catch (SQLException exception) {
            throw failure("No se pudo guardar la fotografía en el rollo", exception);
        }
    }

    synchronized void savePrint(UUID photoId, int mapId, byte[] pixels) {
        savePrint(photoId, mapId, pixels, 1, 0, 0);
    }

    synchronized void savePrint(UUID photoId, int mapId, byte[] pixels,
                                int gridSize, int tileX, int tileY) {
        if (pixels == null || pixels.length != 128 * 128) {
            throw new IllegalArgumentException("El mapa debe contener 16384 píxeles");
        }
        if (gridSize < 1 || tileX < 0 || tileY < 0 || tileX >= gridSize || tileY >= gridSize) {
            throw new IllegalArgumentException("La posición del mapa no es válida");
        }
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO prints(map_id,photo_id,pixels,grid_size,tile_x,tile_y,created_at) "
                        + "VALUES(?,?,?,?,?,?,?)")) {
            statement.setInt(1, mapId);
            statement.setString(2, photoId.toString());
            statement.setBytes(3, pixels);
            statement.setInt(4, gridSize);
            statement.setInt(5, tileX);
            statement.setInt(6, tileY);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("No se pudo guardar la impresión fotográfica", exception);
        }
    }

    synchronized List<PrintRecord> prints() {
        List<PrintRecord> prints = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id,photo_id,pixels,grid_size,tile_x,tile_y FROM prints ORDER BY map_id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                byte[] pixels = result.getBytes("pixels");
                if (pixels != null && pixels.length == 128 * 128) {
                    prints.add(new PrintRecord(result.getInt("map_id"),
                            UUID.fromString(result.getString("photo_id")), pixels,
                            result.getInt("grid_size"), result.getInt("tile_x"), result.getInt("tile_y")));
                }
            }
            return List.copyOf(prints);
        } catch (SQLException exception) {
            throw failure("No se pudieron restaurar las impresiones", exception);
        }
    }

    synchronized Optional<PrintRecord> print(int mapId) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT map_id,photo_id,pixels,grid_size,tile_x,tile_y FROM prints WHERE map_id=?")) {
            statement.setInt(1, mapId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                byte[] pixels = result.getBytes("pixels");
                if (pixels == null || pixels.length != 128 * 128) {
                    return Optional.empty();
                }
                return Optional.of(new PrintRecord(result.getInt("map_id"),
                        UUID.fromString(result.getString("photo_id")), pixels,
                        result.getInt("grid_size"), result.getInt("tile_x"), result.getInt("tile_y")));
            }
        } catch (SQLException exception) {
            throw failure("No se pudo consultar la impresión", exception);
        }
    }

    synchronized void saveRender(UUID photoId, int gridSize, byte[] pixels) {
        int expected = 128 * gridSize * 128 * gridSize;
        if (gridSize < 2 || pixels == null || pixels.length != expected) {
            throw new IllegalArgumentException("La ampliación no tiene dimensiones válidas");
        }
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO photo_renders(photo_id,grid_size,pixels,created_at) VALUES(?,?,?,?)")) {
            statement.setString(1, photoId.toString());
            statement.setInt(2, gridSize);
            statement.setBytes(3, pixels);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("No se pudo guardar la ampliación fotográfica", exception);
        }
    }

    synchronized Optional<byte[]> render(UUID photoId, int gridSize) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT pixels FROM photo_renders WHERE photo_id=? AND grid_size=?")) {
            statement.setString(1, photoId.toString());
            statement.setInt(2, gridSize);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                byte[] pixels = result.getBytes(1);
                int expected = 128 * gridSize * 128 * gridSize;
                return pixels != null && pixels.length == expected
                        ? Optional.of(pixels.clone()) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("No se pudo consultar la ampliación", exception);
        }
    }

    synchronized boolean deleteRender(UUID photoId, int gridSize) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM photo_renders WHERE photo_id=? AND grid_size=?")) {
            statement.setString(1, photoId.toString());
            statement.setInt(2, gridSize);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw failure("No se pudo consumir la ampliación", exception);
        }
    }

    synchronized Album album(UUID cameraId) {
        try (Connection connection = connect()) {
            CameraRecord camera = readCamera(connection, cameraId);
            if (camera == null) {
                return new Album(List.of());
            }
            List<AlbumRoll> rolls = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rolls WHERE camera_id=? ORDER BY position")) {
                statement.setString(1, cameraId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        RollRecord roll = mapRoll(result);
                        rolls.add(new AlbumRoll(roll, photos(connection, roll.id())));
                    }
                }
            }
            return new Album(List.copyOf(rolls));
        } catch (SQLException exception) {
            throw failure("No se pudo abrir el álbum", exception);
        }
    }

    synchronized int remainingExposures(UUID cameraId) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(capacity-exposures),0) FROM rolls WHERE camera_id=? AND state=?")) {
            statement.setString(1, cameraId.toString());
            statement.setString(2, ACTIVE);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo consultar el rollo", exception);
        }
    }

    synchronized boolean destroyCamera(UUID cameraId, long expectedRevision) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement photos = connection.prepareStatement(
                        "DELETE FROM photos WHERE roll_id IN (SELECT id FROM rolls WHERE camera_id=?)")) {
                    photos.setString(1, cameraId.toString());
                    photos.executeUpdate();
                }
                try (PreparedStatement rolls = connection.prepareStatement("DELETE FROM rolls WHERE camera_id=?")) {
                    rolls.setString(1, cameraId.toString());
                    rolls.executeUpdate();
                }
                int changed;
                try (PreparedStatement camera = connection.prepareStatement(
                        "DELETE FROM cameras WHERE id=? AND revision=?")) {
                    camera.setString(1, cameraId.toString());
                    camera.setLong(2, expectedRevision);
                    changed = camera.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo eliminar la cámara", exception);
        }
    }

    synchronized boolean destroyRoll(UUID rollId, long expectedRevision) {
        try (Connection connection = connect()) {
            RollRecord roll = readRoll(connection, rollId);
            if (roll == null || roll.cameraId() != null || roll.revision() != expectedRevision) {
                return false;
            }
            try (PreparedStatement photos = connection.prepareStatement("DELETE FROM photos WHERE roll_id=?")) {
                photos.setString(1, rollId.toString());
                photos.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM rolls WHERE id=? AND revision=? AND camera_id IS NULL")) {
                statement.setString(1, rollId.toString());
                statement.setLong(2, expectedRevision);
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException exception) {
            throw failure("No se pudo eliminar el rollo", exception);
        }
    }

    synchronized Stats stats() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            int cameras;
            int rolls;
            int photos;
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM cameras")) {
                result.next();
                cameras = result.getInt(1);
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM rolls")) {
                result.next();
                rolls = result.getInt(1);
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM prints")) {
                result.next();
                photos = result.getInt(1);
            }
            return new Stats(cameras, rolls, photos);
        } catch (SQLException exception) {
            throw failure("No se pudieron consultar las estadísticas", exception);
        }
    }

    private List<PhotoRecord> photos(Connection connection, UUID rollId) throws SQLException {
        List<PhotoRecord> photos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM photos WHERE roll_id=? AND image IS NOT NULL ORDER BY slot")) {
            statement.setString(1, rollId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    photos.add(new PhotoRecord(
                            UUID.fromString(result.getString("id")),
                            rollId,
                            result.getInt("slot"),
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("player_name"),
                            result.getLong("created_at"),
                            result.getString("caption"),
                            result.getBytes("image")
                    ));
                }
            }
        }
        return List.copyOf(photos);
    }

    private RollRecord firstAvailableRoll(Connection connection, UUID cameraId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rolls WHERE camera_id=? AND state=? AND exposures<capacity ORDER BY position LIMIT 1")) {
            statement.setString(1, cameraId.toString());
            statement.setString(2, ACTIVE);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapRoll(result) : null;
            }
        }
    }

    private CameraRecord readCamera(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM cameras WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new CameraRecord(id, result.getLong("revision"), result.getString("state"),
                        result.getInt("damage"), result.getInt("shutter_count"), result.getInt("max_damage"));
            }
        }
    }

    private RollRecord readRoll(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rolls WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapRoll(result) : null;
            }
        }
    }

    private static RollRecord mapRoll(ResultSet result) throws SQLException {
        String camera = result.getString("camera_id");
        int position = result.getInt("position");
        return new RollRecord(UUID.fromString(result.getString("id")), result.getLong("revision"),
                result.getString("state"), result.getInt("exposures"), result.getInt("capacity"),
                camera == null ? null : UUID.fromString(camera), camera == null ? null : position);
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static void update(Connection connection, String sql, long now, String id, long revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setString(2, id);
            statement.setLong(3, revision);
            requireOne(statement.executeUpdate());
        }
    }

    private static void requireOne(int changed) throws SQLException {
        if (changed != 1) {
            throw new SQLException("La revisión cambió durante la operación");
        }
    }

    private static <T> T rollback(Connection connection, T value) throws SQLException {
        connection.rollback();
        return value;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static IllegalStateException failure(String message, SQLException exception) {
        return new IllegalStateException(message + ": " + exception.getMessage(), exception);
    }

    @Override
    public void close() {
        // Las conexiones son breves y se cierran en cada operación.
    }

    record CameraRecord(UUID id, long revision, String state, int damage, int shutterCount, int maxDamage) {
    }

    record RollRecord(UUID id, long revision, String state, int exposures, int capacity,
                      UUID cameraId, Integer position) {
    }

    record PhotoRecord(UUID id, UUID rollId, int slot, UUID playerId, String playerName,
                       long createdAt, String caption, byte[] image) {
        PhotoRecord {
            image = image.clone();
        }

        @Override
        public byte[] image() {
            return image.clone();
        }
    }

    record PrintRecord(int mapId, UUID photoId, byte[] pixels, int gridSize, int tileX, int tileY) {
        PrintRecord {
            pixels = pixels.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }

    record AlbumRoll(RollRecord roll, List<PhotoRecord> photos) {
    }

    record Album(List<AlbumRoll> rolls) {
    }

    record Stats(int cameras, int rolls, int photos) {
    }

    record Attachment(boolean success, String message, CameraRecord camera, RollRecord roll) {
        static Attachment success(CameraRecord camera, RollRecord roll) {
            return new Attachment(true, "", camera, roll);
        }

        static Attachment failure(String message) {
            return new Attachment(false, message, null, null);
        }
    }

    enum ShotStatus { OK, UNKNOWN, DUPLICATE, WORN, NO_FILM }

    record ShotResult(ShotStatus status, String message, UUID photoId, CameraRecord camera,
                      RollRecord roll, boolean rollConsumed) {
        static ShotResult success(UUID photoId, CameraRecord camera, RollRecord roll, boolean rollConsumed) {
            return new ShotResult(ShotStatus.OK, "", photoId, camera, roll, rollConsumed);
        }

        static ShotResult failure(ShotStatus status, String message) {
            return new ShotResult(status, message, null, null, null, false);
        }

        boolean success() {
            return status == ShotStatus.OK;
        }
    }
}
