package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;

/** Builds the user message for one chunk. The system prompt is resources/prompts/translate-v1.txt. */
final class TranslationPrompt {

    private TranslationPrompt() {}

    static String userMessage(VideoInfo video, Chunk chunk) {
        StringBuilder message = new StringBuilder();
        message.append("Title: ").append(video.title()).append('\n');
        message.append("Channel: ").append(video.channel()).append('\n');
        message.append("Register: casual Korean 반말.\n");
        if (!chunk.context().isEmpty()) {
            message.append("\nPrevious context:\n");
            for (Sentence sentence : chunk.context()) {
                message.append(line(sentence)).append('\n');
            }
        }
        message.append("\nCaptions:\n");
        List<Sentence> sentences = chunk.sentences();
        for (int i = 0; i < sentences.size(); i++) {
            message.append(i + 1).append('|').append(line(sentences.get(i))).append('\n');
        }
        return message.toString();
    }

    private static String line(Sentence sentence) {
        return (sentence.newSpeaker() ? ">> " : "") + sentence.text();
    }
}
