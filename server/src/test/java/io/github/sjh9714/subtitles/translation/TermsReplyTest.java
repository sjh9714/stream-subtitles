package io.github.sjh9714.subtitles.translation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sjh9714.subtitles.translation.Glossary.Group;
import io.github.sjh9714.subtitles.translation.Glossary.Term;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermsReplyTest {

    @Test
    void readsOneGroupPerLineWithEveryHeardForm() {
        List<Group> groups = TermsReply.parse(
                """
                Gigi Mooney = Gigi Murin | this channel's streamer
                Naboro / Naburu = 나보루 | the game The Legend of Zelda
                """,
                "a channel",
                "aaaaaaaaaaa");

        assertThat(groups)
                .containsExactly(
                        new Group(
                                "a channel",
                                null,
                                "this channel's streamer",
                                "aaaaaaaaaaa",
                                List.of(new Term(List.of("Gigi Mooney"), "Gigi Murin"))),
                        new Group(
                                "a channel",
                                null,
                                "the game The Legend of Zelda",
                                "aaaaaaaaaaa",
                                List.of(new Term(List.of("Naboro", "Naburu"), "나보루"))));
    }

    @Test
    void skipsALineInAnotherShapeOrWithAnOverlongField() {
        String reply = "Here are the terms:\n\nMooney = Murin\nGrams = Grems | the fans\nLiz = " + "x".repeat(81)
                + " | a streamer\n";

        assertThat(TermsReply.parse(reply, "a channel", "aaaaaaaaaaa"))
                .extracting(group -> group.terms().getFirst().write())
                .containsExactly("Grems");
    }

    @Test
    void emptyReplyHasNoTerms() {
        assertThat(TermsReply.parse("", "a channel", "aaaaaaaaaaa")).isEmpty();
    }
}
