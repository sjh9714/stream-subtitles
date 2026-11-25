package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.translation.Translator.Reply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * What this month's model calls cost. Every call is written down when its reply arrives, also when the
 * reply turns out to be unusable, and no call is made once the month's cap is reached.
 */
@Repository
public class Spending {

    private final JdbcClient jdbc;
    private final double capUsd;

    Spending(JdbcClient jdbc, @Value("${subtitles.monthly-cap-usd}") double capUsd) {
        this.jdbc = jdbc;
        this.capUsd = capUsd;
    }

    /** Whether model calls must wait for next month. A cap of zero stops them at once. */
    public boolean capReached() {
        Double usd = jdbc.sql("select coalesce(sum(usd), 0) from model_call where created_at >= date_trunc('month', now())")
                .query(Double.class)
                .single();
        return usd >= capUsd;
    }

    void record(Model model, Reply reply) {
        jdbc.sql("insert into model_call (model, input_tokens, output_tokens, usd) values (:model, :input, :output, :usd)")
                .param("model", model.id())
                .param("input", reply.inputTokens())
                .param("output", reply.outputTokens())
                .param("usd", model.usd(reply.inputTokens(), reply.outputTokens()))
                .update();
    }
}
