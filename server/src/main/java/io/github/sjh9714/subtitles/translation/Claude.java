package io.github.sjh9714.subtitles.translation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Sends one request to a Claude model. Reads ANTHROPIC_API_KEY from the environment on first use. */
@Component
class Claude {

    /** Thinking tokens count as output, so the limit leaves room for them. Streaming avoids an HTTP timeout. */
    private static final long MAX_TOKENS = 48_000;

    private AnthropicClient client;

    Reply ask(String model, String systemPrompt, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(userMessage)
                .build();

        MessageAccumulator accumulator = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params)) {
            stream.stream().forEach(accumulator::accumulate);
        }
        Message message = accumulator.message();
        if (!message.stopReason().map(StopReason.END_TURN::equals).orElse(false)) {
            throw new IllegalStateException("the model stopped early: " + message.stopReason());
        }

        String reply = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .collect(Collectors.joining());
        return new Reply(reply, message.usage().inputTokens(), message.usage().outputTokens());
    }

    static String readPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private synchronized AnthropicClient client() {
        if (client == null) {
            client = AnthropicOkHttpClient.fromEnv();
        }
        return client;
    }
}
