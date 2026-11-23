package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;
import java.util.stream.Collectors;

/** Builds the user message for one chunk. The system prompt is resources/prompts/translate-v1.txt. */
final class TranslationPrompt {

    private TranslationPrompt() {}

    static String userMessage(VideoInfo video, Chunk chunk) {
        StringBuilder message = new StringBuilder();
        message.append("Title: ").append(video.title()).append('\n');
        message.append("Channel: ").append(video.channel()).append('\n');
        message.append("Register: casual Korean 반말.\n");
        message.append(glossary(video, chunk));
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

    /** The glossary terms heard in this chunk, so a long glossary does not ride along with every call. */
    private static String glossary(VideoInfo video, Chunk chunk) {
        String text = chunk.sentences().stream().map(Sentence::text).collect(Collectors.joining("\n"));
        StringBuilder block = new StringBuilder();
        for (Glossary.Group group : video.glossary()) {
            for (Glossary.Term term : group.terms()) {
                term.heard().stream()
                        .filter(heard -> Glossary.mentions(text, heard))
                        .forEach(heard -> block.append("- ")
                                .append(heard)
                                .append(" = ")
                                .append(term.write())
                                .append(" (")
                                .append(group.about())
                                .append(")\n"));
            }
        }
        return block.isEmpty() ? "" : "\nGlossary:\n" + block;
    }

    private static String line(Sentence sentence) {
        return (sentence.newSpeaker() ? ">> " : "") + sentence.text();
    }
}
