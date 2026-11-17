package io.github.sjh9714.subtitles.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sjh9714.subtitles.translation.TranslationReply.MalformedReplyException;
import org.junit.jupiter.api.Test;

class TranslationReplyTest {

    @Test
    void linesComeBackInNumberOrder() {
        assertThat(TranslationReply.parse("2|둘\n1|하나\n", 2)).containsExactly("하나", "둘");
    }

    @Test
    void emptyTranslationIsAllowed() {
        assertThat(TranslationReply.parse("1|\n2|응|그래", 2)).containsExactly("", "응|그래");
    }

    @Test
    void missingLineIsRejected() {
        assertThatThrownBy(() -> TranslationReply.parse("1|하나", 2)).isInstanceOf(MalformedReplyException.class);
    }

    @Test
    void repeatedOrOutOfRangeNumberIsRejected() {
        assertThatThrownBy(() -> TranslationReply.parse("1|하나\n1|또", 2)).isInstanceOf(MalformedReplyException.class);
        assertThatThrownBy(() -> TranslationReply.parse("1|하나\n3|셋", 2)).isInstanceOf(MalformedReplyException.class);
    }

    @Test
    void textOutsideTheFormatIsRejected() {
        assertThatThrownBy(() -> TranslationReply.parse("번역입니다\n1|하나", 1)).isInstanceOf(MalformedReplyException.class);
    }
}
