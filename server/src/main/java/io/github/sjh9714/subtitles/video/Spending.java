package io.github.sjh9714.subtitles.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * What this month's model calls cost, summed from the usage rows. A chunk that failed every attempt
 * leaves no row, so the sum can be a little below the bill.
 */
@Repository
class Spending {

    /**
     * Prices in USD per million tokens as of 2026-11-23: Claude Haiku 5.5, which translates, is 0.10 in
     * and 0.50 out. Claude Opus 5.5, which extracts terms, is 4 in and 20 out.
     */
    private static final String THIS_MONTH_USD = """
            select ((select coalesce(sum(input_tokens * 0.10 + output_tokens * 0.50), 0) from translation_chunk
                     where created_at >= date_trunc('month', now()))
                  + (select coalesce(sum(input_tokens * 4 + output_tokens * 20), 0) from term_extraction
                     where created_at >= date_trunc('month', now()))) / 1000000
            """;

    private final JdbcClient jdbc;
    private final double capUsd;

    Spending(JdbcClient jdbc, @Value("${subtitles.monthly-cap-usd}") double capUsd) {
        this.jdbc = jdbc;
        this.capUsd = capUsd;
    }

    /** Whether new translations must wait for next month. A cap of zero stops them at once. */
    boolean capReached() {
        return jdbc.sql(THIS_MONTH_USD).query(Double.class).single() >= capUsd;
    }
}
