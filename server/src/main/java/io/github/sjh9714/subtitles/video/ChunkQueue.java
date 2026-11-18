package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Chunker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One video's chunks that are not translated yet. They are handed out from the place the viewer is
 * watching onward, and the part before it comes last, so the wait is for one chunk and not the video.
 */
final class ChunkQueue {

    private final List<Chunk> waiting;
    /** Chunks that are waiting or being translated right now. */
    private final Set<Integer> unfinished = new HashSet<>();
    private long watchedWindow;

    /** @param chunks in video order */
    ChunkQueue(List<Chunk> chunks, long positionMs) {
        this.waiting = new ArrayList<>(chunks);
        chunks.forEach(chunk -> unfinished.add(chunk.index()));
        this.watchedWindow = positionMs / Chunker.WINDOW_MS;
    }

    /** Moves the starting point to where the viewer is now. Returns whether that place is translated. */
    synchronized boolean watch(long positionMs) {
        watchedWindow = positionMs / Chunker.WINDOW_MS;
        return !unfinished.contains((int) watchedWindow);
    }

    /** The next chunk to translate, or null when none is waiting. */
    synchronized Chunk next() {
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).index() >= watchedWindow) {
                return waiting.remove(i);
            }
        }
        return waiting.isEmpty() ? null : waiting.removeFirst();
    }

    synchronized void finished(Chunk chunk) {
        unfinished.remove(chunk.index());
    }
}
