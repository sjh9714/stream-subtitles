package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import java.util.ArrayList;
import java.util.List;

/** Splits a video's sentences into fixed windows of video time so a long video becomes many small calls. */
public final class Chunker {

    static final long WINDOW_MS = 300_000;
    static final int CONTEXT_SENTENCES = 3;

    private Chunker() {}

    public static List<Chunk> split(List<Sentence> sentences) {
        List<Chunk> chunks = new ArrayList<>();
        int from = 0;
        while (from < sentences.size()) {
            long window = sentences.get(from).startMs() / WINDOW_MS;
            int to = from;
            while (to < sentences.size() && sentences.get(to).startMs() / WINDOW_MS == window) {
                to++;
            }
            List<Sentence> context = sentences.subList(Math.max(0, from - CONTEXT_SENTENCES), from);
            chunks.add(new Chunk(chunks.size(), List.copyOf(context), List.copyOf(sentences.subList(from, to))));
            from = to;
        }
        return chunks;
    }
}
