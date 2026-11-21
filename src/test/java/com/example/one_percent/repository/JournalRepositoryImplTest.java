package com.example.one_percent.repository;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class JournalRepositoryImplTest {

    private static boolean finds(String search, String text) {
        return Pattern.compile(JournalRepositoryImpl.literalRegex(search), Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    @Test
    void searchTextIsMatchedLiterally() {
        assertThat(finds("c++", "learning C++ today")).isTrue();
        assertThat(finds("(draft", "notes (draft 2)")).isTrue();
        assertThat(finds("a.b", "a.b")).isTrue();
        assertThat(finds("a.b", "axb")).isFalse();   // "." isn't a wildcard
        assertThat(finds(".*", "anything")).isFalse();
        assertThat(finds("$5 [cash]", "spent $5 [cash] on coffee")).isTrue();
        assertThat(finds("back\\slash", "a back\\slash")).isTrue();
    }

    @Test
    void lettersOutsideAsciiAreLeftAlone() {
        assertThat(JournalRepositoryImpl.literalRegex("café")).isEqualTo("café");
        assertThat(finds("café", "a Café visit")).isTrue();
    }
}
