package ru.lab15.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IsbnTest {

    @Test
    void normalizeRemovesHyphensAndSpaces() {
        assertEquals("9785170903352", LibraryService.normalizeIsbn("978-5-17-090335-2"));
        assertEquals("080442957X", LibraryService.normalizeIsbn("0 8044 2957 x"));
    }

    @Test
    void blankIsbnBecomesNull() {
        assertNull(LibraryService.normalizeIsbn("  "));
        assertNull(LibraryService.normalizeIsbn(null));
    }
}
