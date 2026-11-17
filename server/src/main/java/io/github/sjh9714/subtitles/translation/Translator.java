package io.github.sjh9714.subtitles.translation;

/** Asks the model to translate one chunk. The caller checks the reply with {@link TranslationReply}. */
public interface Translator {

    Reply translate(VideoInfo video, Chunk chunk);

    /** What the model is told about the video. */
    record VideoInfo(String title, String channel) {}

    /** The model's raw text and what the call was billed for. */
    record Reply(String text, long inputTokens, long outputTokens) {}
}
