package io.github.sjh9714.subtitles.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceRegrouperTest {

    @Test
    void sentenceCutAcrossCaptionLinesBecomesOneSubtitleStartingAtItsFirstWord() {
        List<Sentence> sentences = SentenceRegrouper.regroup(List.of(
                new Word("It", 1000, false), new Word("was", 1200, false), new Word("late.", 1500, false),
                new Word("So", 2100, false), new Word("I", 2300, false), new Word("left.", 2600, false)));

        assertThat(sentences).containsExactly(
                new Sentence(1000, 2100, "It was late.", false),
                new Sentence(2100, 5600, "So I left.", false));
    }

    @Test
    void speakerChangeEndsTheSentenceEvenWithoutPunctuation() {
        List<Sentence> sentences = SentenceRegrouper.regroup(List.of(
                new Word("wait", 0, false), new Word("what", 300, false),
                new Word("No.", 900, true)));

        assertThat(sentences).containsExactly(
                new Sentence(0, 900, "wait what", false),
                new Sentence(900, 3900, "No.", true));
    }

    @Test
    void subtitleDoesNotLingerPastThePauseAfterItsLastWord() {
        List<Sentence> sentences = SentenceRegrouper.regroup(List.of(
                new Word("Done.", 0, false), new Word("Next.", 60_000, false)));

        assertThat(sentences.getFirst().endMs()).isEqualTo(SentenceRegrouper.LINGER_MS);
    }

    @Test
    void runWithoutPunctuationIsCutAtTheWordLimit() {
        List<Word> words = new ArrayList<>();
        for (int i = 0; i < SentenceRegrouper.MAX_WORDS + 5; i++) {
            words.add(new Word("and", i * 100L, false));
        }

        List<Sentence> sentences = SentenceRegrouper.regroup(words);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(1).startMs()).isEqualTo(SentenceRegrouper.MAX_WORDS * 100L);
    }
}
