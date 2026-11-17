package io.github.sjh9714.subtitles.caption;

/** A run of words shown as one subtitle. Times come from the words, never from the model. */
public record Sentence(long startMs, long endMs, String text, boolean newSpeaker) {}
