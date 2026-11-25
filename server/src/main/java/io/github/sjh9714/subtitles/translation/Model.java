package io.github.sjh9714.subtitles.translation;

/** A Claude model and what it costs in USD per million tokens. */
record Model(String id, double inputUsd, double outputUsd) {

    double usd(long inputTokens, long outputTokens) {
        return (inputTokens * inputUsd + outputTokens * outputUsd) / 1_000_000;
    }
}
