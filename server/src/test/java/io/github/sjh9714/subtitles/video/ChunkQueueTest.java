package io.github.sjh9714.subtitles.video;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Chunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkQueueTest {

    private static Chunk chunk(int index) {
        return new Chunk(index, List.of(), List.of());
    }

    @Test
    void chunksComeFromTheViewersPositionOnwardThenTheEarlierPart() {
        ChunkQueue queue = new ChunkQueue(
                List.of(chunk(0), chunk(1), chunk(2), chunk(3)), 2 * Chunker.WINDOW_MS + 5);

        assertThat(List.of(queue.next(), queue.next(), queue.next(), queue.next()))
                .extracting(Chunk::index)
                .containsExactly(2, 3, 0, 1);
        assertThat(queue.next()).isNull();
    }

    @Test
    void movingTheViewerChangesWhatComesNext() {
        ChunkQueue queue = new ChunkQueue(List.of(chunk(0), chunk(1), chunk(2), chunk(3)), 0);

        assertThat(queue.next().index()).isEqualTo(0);
        queue.watch(3 * Chunker.WINDOW_MS);
        assertThat(queue.next().index()).isEqualTo(3);
        assertThat(queue.next().index()).isEqualTo(1);
    }

    @Test
    void positionCountsAsTranslatedOnlyAfterItsChunkIsFinished() {
        ChunkQueue queue = new ChunkQueue(List.of(chunk(0), chunk(2)), 0);

        Chunk first = queue.next();
        assertThat(queue.watch(0)).isFalse();
        queue.finished(first);
        assertThat(queue.watch(0)).isTrue();
        // Nothing is said in the second window, so there is nothing to wait for there.
        assertThat(queue.watch(Chunker.WINDOW_MS)).isTrue();
        assertThat(queue.watch(2 * Chunker.WINDOW_MS)).isFalse();
    }
}
