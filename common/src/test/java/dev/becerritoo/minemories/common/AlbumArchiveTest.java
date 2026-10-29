package dev.becerritoo.minemories.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlbumArchiveTest {
    @Test
    void albumRoundTrip() throws Exception {
        var photo = new AlbumArchive.Photo("photo", 1, 1234L, "Alex", "Una montaña",
                new byte[]{1, 2, 3});
        var input = new AlbumArchive.Album(List.of(
                new AlbumArchive.Roll("roll", 1, 12, List.of(photo))));
        var output = AlbumArchive.decode(AlbumArchive.encode(input));
        assertEquals("Una montaña", output.rolls().getFirst().photos().getFirst().caption());
        assertArrayEquals(photo.jpeg(), output.rolls().getFirst().photos().getFirst().jpeg());
    }

    @Test
    void rejectsTrailingData() {
        byte[] valid = AlbumArchive.encode(new AlbumArchive.Album(List.of()));
        byte[] invalid = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(java.io.IOException.class, () -> AlbumArchive.decode(invalid));
    }
}
