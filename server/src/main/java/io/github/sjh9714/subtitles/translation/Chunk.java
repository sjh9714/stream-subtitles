package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import java.util.List;

/** The sentences sent to the model in one call, plus the sentences just before them for continuity. */
public record Chunk(int index, List<Sentence> context, List<Sentence> sentences) {}
