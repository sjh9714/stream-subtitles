package io.github.sjh9714.subtitles.translation;

import java.util.List;

/** Asks the model to translate one chunk. The caller checks the reply with {@link TranslationReply}. */
public interface Translator {

    Reply translate(VideoInfo video, Chunk chunk);

    /** What the model is told about the video. The glossary holds the term groups that can apply to it. */
    record VideoInfo(String title, String channel, List<Glossary.Group> glossary) {}

    /** The model's raw text and what the call was billed for. */
    record Reply(String text, long inputTokens, long outputTokens) {}
}
