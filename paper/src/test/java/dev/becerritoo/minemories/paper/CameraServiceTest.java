package dev.becerritoo.minemories.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraServiceTest {
    @Test
    void usesASafeCaptionAsThePrintedTitle() {
        assertEquals("Fotografía Minemories", CameraService.itemTitle("  "));
        assertEquals("Atardecer dorado", CameraService.itemTitle("§cAtardecer\ndorado"));

        String longCaption = "a".repeat(80);
        assertEquals(64, CameraService.itemTitle(longCaption).codePointCount(
                0, CameraService.itemTitle(longCaption).length()));
    }
}
