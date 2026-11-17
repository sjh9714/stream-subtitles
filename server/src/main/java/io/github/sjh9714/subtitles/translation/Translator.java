package io.github.sjh9714.subtitles.translation;

import java.util.List;

/** Translates one chunk. The reply has exactly one line per sentence, in order. */
public interface Translator {

    Translation translate(VideoInfo video, Chunk chunk);

    /** What the model is told about the video. */
    record VideoInfo(String title, String channel) {}

    /** A blank line means the sentence has nothing worth showing, such as a lone filler. */
    record Translation(List<String> lines, long inputTokens, long outputTokens) {}
}
