package io.github.sjh9714.subtitles.video;

public record Subtitle(int seq, long startMs, long endMs, String text) {}
