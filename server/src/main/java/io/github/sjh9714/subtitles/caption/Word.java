package io.github.sjh9714.subtitles.caption;

/** A spoken word with its start time. {@code newSpeaker} is true when a speaker-change mark preceded it. */
public record Word(String text, long startMs, boolean newSpeaker) {}
