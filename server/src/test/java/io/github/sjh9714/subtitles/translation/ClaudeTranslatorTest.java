package io.github.sjh9714.subtitles.translation;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ClaudeTranslatorTest {

    /** The server must start, and serve stored subtitles, on a machine that has no API key. */
    @Test
    void canBeCreatedWithoutAnApiKey() {
        assertThatCode(ClaudeTranslator::new).doesNotThrowAnyException();
    }
}
