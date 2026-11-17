package io.github.sjh9714.subtitles.caption;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sjh9714.subtitles.caption.CaptionEvent.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptionParserTest {

    @Test
    void wordStartIsLineStartPlusOffset() {
        List<Word> words = CaptionParser.parse(List.of(
                new CaptionEvent(1000, List.of(new Segment("so", null), new Segment(" we", 400L)))));

        assertThat(words).containsExactly(new Word("so", 1000, false), new Word("we", 1400, false));
    }

    @Test
    void speakerMarkFlagsTheNextWordAndIsNotAWord() {
        List<Word> words = CaptionParser.parse(List.of(
                new CaptionEvent(0, List.of(new Segment("right.", 0L), new Segment(">>", 500L), new Segment(" Sure.", 600L)))));

        assertThat(words).containsExactly(new Word("right.", 0, false), new Word("Sure.", 600, true));
    }

    @Test
    void lineWithoutWordTimingSharesTheLineStart() {
        List<Word> words = CaptionParser.parse(List.of(new CaptionEvent(2000, List.of(new Segment(">> hold on a second", null)))));

        assertThat(words).extracting(Word::startMs).containsOnly(2000L);
        assertThat(words).extracting(Word::newSpeaker).containsExactly(true, false, false, false);
    }

    @Test
    void nonSpeechTagsAndEmptyEventsAreSkipped() {
        List<Word> words = CaptionParser.parse(List.of(
                new CaptionEvent(0, null),
                new CaptionEvent(0, List.of(new Segment("\n", null))),
                new CaptionEvent(100, List.of(new Segment("[laughter]", 0L), new Segment(" [clears throat]", 200L), new Segment(" okay", 900L)))));

        assertThat(words).containsExactly(new Word("okay", 1000, false));
    }
}
