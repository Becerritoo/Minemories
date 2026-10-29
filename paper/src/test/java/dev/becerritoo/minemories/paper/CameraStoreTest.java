package dev.becerritoo.minemories.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraStoreTest {
    @TempDir
    Path temporary;

    @Test
    void advancesRevisionsAndRejectsAStaleCopy() {
        try (CameraStore store = new CameraStore(temporary.toFile())) {
            var camera = store.createCamera(64);
            var roll = store.createRoll(12);
            var attachment = store.attach(camera.id(), camera.revision(), roll.id(), roll.revision(), 3);
            assertTrue(attachment.success());

            var first = store.reserveShot(camera.id(), attachment.camera().revision(), true);
            assertTrue(first.success());
            assertEquals(1, first.camera().damage());
            assertEquals(1, first.roll().exposures());

            var duplicate = store.reserveShot(camera.id(), attachment.camera().revision(), true);
            assertFalse(duplicate.success());
            assertEquals(CameraStore.ShotStatus.DUPLICATE, duplicate.status());

            assertEquals(0, store.album(camera.id()).rolls().getFirst().photos().size());
        }
    }

    @Test
    void repairsAndDeletesOnlyTheCurrentRevision() {
        try (CameraStore store = new CameraStore(temporary.toFile())) {
            var camera = store.createCamera(8);
            var roll = store.createRoll(2);
            var attachment = store.attach(camera.id(), camera.revision(), roll.id(), roll.revision(), 1);
            var shot = store.reserveShot(camera.id(), attachment.camera().revision(), true);
            var repaired = store.repair(camera.id(), shot.camera().revision(), 0).orElseThrow();
            assertEquals(0, repaired.damage());
            assertFalse(store.destroyCamera(camera.id(), shot.camera().revision()));
            assertTrue(store.destroyCamera(camera.id(), repaired.revision()));
            assertTrue(store.camera(camera.id()).isEmpty());
        }
    }

    @Test
    void persistsMapPixelsIndependentlyFromTheRoll() {
        try (CameraStore store = new CameraStore(temporary.toFile())) {
            var camera = store.createCamera(8);
            var roll = store.createRoll(2);
            var attachment = store.attach(camera.id(), camera.revision(), roll.id(), roll.revision(), 1);
            var shot = store.reserveShot(camera.id(), attachment.camera().revision(), true);
            byte[] pixels = new byte[128 * 128];
            pixels[42] = 12;

            store.savePrint(shot.photoId(), 91, pixels);
            assertEquals(1, store.prints().size());
            assertEquals(12, store.prints().getFirst().pixels()[42]);
            assertEquals(1, store.print(91).orElseThrow().gridSize());

            assertTrue(store.destroyCamera(camera.id(), shot.camera().revision()));
            assertEquals(1, store.prints().size());
        }
    }

    @Test
    void storesHighResolutionSourceAndMarksItsFourMapTiles() {
        try (CameraStore store = new CameraStore(temporary.toFile())) {
            var photoId = java.util.UUID.randomUUID();
            byte[] mural = new byte[256 * 256];
            java.util.Arrays.fill(mural, (byte) 4);
            byte[] tile = new byte[128 * 128];

            store.saveRender(photoId, 2, mural);
            store.savePrint(photoId, 101, tile, 2, 1, 0);

            assertEquals(256 * 256, store.render(photoId, 2).orElseThrow().length);
            var print = store.print(101).orElseThrow();
            assertEquals(2, print.gridSize());
            assertEquals(1, print.tileX());
            assertEquals(0, print.tileY());
            assertTrue(store.deleteRender(photoId, 2));
            assertTrue(store.render(photoId, 2).isEmpty());
            assertFalse(store.deleteRender(photoId, 2));
        }
    }

    @Test
    void consumesOnlyTheExhaustedRollAndKeepsTheNextOneLoaded() {
        try (CameraStore store = new CameraStore(temporary.toFile())) {
            var camera = store.createCamera(8);
            var firstRoll = store.createRoll(1);
            var firstAttachment = store.attach(camera.id(), camera.revision(),
                    firstRoll.id(), firstRoll.revision(), 3);
            var secondRoll = store.createRoll(2);
            var secondAttachment = store.attach(camera.id(), firstAttachment.camera().revision(),
                    secondRoll.id(), secondRoll.revision(), 3);

            var shot = store.reserveShot(camera.id(), secondAttachment.camera().revision(), true);

            assertTrue(shot.success());
            assertTrue(shot.rollConsumed());
            assertTrue(store.roll(firstRoll.id()).isEmpty());
            assertTrue(store.roll(secondRoll.id()).isPresent());
            assertEquals(2, store.remainingExposures(camera.id()));
        }
    }
}
