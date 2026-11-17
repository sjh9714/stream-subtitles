package io.github.sjh9714.subtitles.translation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sjh9714.subtitles.caption.Sentence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static Sentence at(long startMs) {
        return new Sentence(startMs, startMs + 1000, "s" + startMs, false);
    }

    @Test
    void sentencesAreGroupedByWindowOfVideoTime() {
        List<Chunk> chunks = Chunker.split(List.of(at(0), at(299_999), at(300_000), at(900_000)));

        assertThat(chunks).extracting(Chunk::index).containsExactly(0, 1, 2);
        assertThat(chunks.get(0).sentences()).containsExactly(at(0), at(299_999));
        assertThat(chunks.get(1).sentences()).containsExactly(at(300_000));
        assertThat(chunks.get(2).sentences()).containsExactly(at(900_000));
    }

    @Test
    void chunkCarriesTheSentencesJustBeforeItAsContext() {
        List<Chunk> chunks = Chunker.split(List.of(at(1), at(2), at(3), at(4), at(300_000)));

        assertThat(chunks.get(0).context()).isEmpty();
        assertThat(chunks.get(1).context()).containsExactly(at(2), at(3), at(4));
    }

    @Test
    void noSentencesMeansNoChunks() {
        assertThat(Chunker.split(List.of())).isEmpty();
    }
}
