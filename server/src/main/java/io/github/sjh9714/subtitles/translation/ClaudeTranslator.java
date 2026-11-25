package io.github.sjh9714.subtitles.translation;

import org.springframework.stereotype.Component;

/** Translates a chunk with Claude Haiku. */
@Component
class ClaudeTranslator implements Translator {

    private static final String MODEL = "claude-haiku-5-5";

    private final String systemPrompt = Claude.readPrompt("prompts/translate-v1.txt");
    private final Claude claude;

    ClaudeTranslator(Claude claude) {
        this.claude = claude;
    }

    @Override
    public Reply translate(VideoInfo video, Chunk chunk) {
        Reply reply = claude.ask(MODEL, systemPrompt, TranslationPrompt.userMessage(video, chunk));
        return new Reply(
                TranslationPrompt.withoutMarks(video, chunk, reply.text()), reply.inputTokens(), reply.outputTokens());
    }
}
