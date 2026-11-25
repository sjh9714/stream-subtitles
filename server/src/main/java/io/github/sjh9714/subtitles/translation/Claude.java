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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Sends one request to a Claude model. Every model call goes through here, so this is where the
 * month's spending is counted and stopped. Reads ANTHROPIC_API_KEY from the environment on first use.
 */
@Component
class Claude {

    /** Thinking tokens count as output, so the limit leaves room for them. Streaming avoids an HTTP timeout. */
    private static final long MAX_TOKENS = 48_000;
    /** How many requests are with the models at the same time, over all videos. */
    private static final int MAX_CALLS = 8;

    private final Semaphore calls = new Semaphore(MAX_CALLS);
    private final Spending spending;

    private AnthropicClient client;

    Claude(Spending spending) {
        this.spending = spending;
    }

    Reply ask(Model model, String systemPrompt, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model.id())
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(userMessage)
                .build();

        MessageAccumulator accumulator = MessageAccumulator.create();
        calls.acquireUninterruptibly();
        try {
            if (spending.capReached()) {
                throw new IllegalStateException("this month's spending cap is reached");
            }
            try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params)) {
                stream.stream().forEach(accumulator::accumulate);
            }
        } finally {
            calls.release();
        }
        Message message = accumulator.message();
        Reply reply = new Reply(
                message.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(text -> text.text())
                        .collect(Collectors.joining()),
                message.usage().inputTokens(),
                message.usage().outputTokens());
        spending.record(model, reply); // a reply that stopped early is billed too
        if (!message.stopReason().map(StopReason.END_TURN::equals).orElse(false)) {
            throw new IllegalStateException("the model stopped early: " + message.stopReason());
        }
        return reply;
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
