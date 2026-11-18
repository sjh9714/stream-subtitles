package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import java.util.List;

/**
 * The sentences sent to the model in one call, plus the sentences just before them for continuity.
 * The index is the number of the chunk's window of video time, so it stays the same when the video is sent again.
 */
public record Chunk(int index, List<Sentence> context, List<Sentence> sentences) {}
