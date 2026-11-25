package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Extracts terms with Claude Opus. When tried on the evaluation videos, Haiku made a wrong entry and
 * Sonnet gave almost no Korean forms.
 */
@Component
class ClaudeTermExtractor implements TermExtractor {

    private static final String MODEL = "claude-opus-5-5";

    private final String systemPrompt = Claude.readPrompt("prompts/extract-terms-v1.txt");
    private final Claude claude;

    ClaudeTermExtractor(Claude claude) {
        this.claude = claude;
    }

    @Override
    public Reply extract(VideoInfo video, List<Sentence> sample) {
        return claude.ask(MODEL, systemPrompt, userMessage(video, sample));
    }

    static String userMessage(VideoInfo video, List<Sentence> sample) {
        StringBuilder message = new StringBuilder();
        message.append("Title: ").append(video.title()).append('\n');
        message.append("Channel: ").append(video.channel()).append('\n');
        String known = TranslationPrompt.termsHeard(video, sample);
        if (!known.isEmpty()) {
            message.append("\nKnown terms:\n").append(known);
        }
        message.append("\nCaptions:\n");
        sample.forEach(sentence -> message.append(sentence.text()).append('\n'));
        return message.toString();
    }
}
