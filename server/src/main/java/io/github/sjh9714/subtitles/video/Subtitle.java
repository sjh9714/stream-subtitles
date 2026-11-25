package io.github.sjh9714.subtitles.video;

/** @param chunk the chunk it was translated in. A viewer asks only for the chunks it does not have yet. */
public record Subtitle(int chunk, int seq, long startMs, long endMs, String text) {}
