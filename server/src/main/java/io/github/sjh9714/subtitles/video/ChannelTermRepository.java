package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.translation.Glossary.Group;
import io.github.sjh9714.subtitles.translation.Glossary.Term;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The terms the server extracted from the videos of a channel. */
@Repository
public class ChannelTermRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    ChannelTermRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One group per stored term, oldest first. The group's source is the video the term came from. */
    public List<Group> find(String channel) {
        return jdbc.sql("""
                        select heard, written, about, youtube_id from channel_term
                        where channel = :channel order by created_at, heard
                        """)
                .param("channel", channel)
                .query((row, n) -> new Group(
                        channel,
                        null,
                        row.getString("about"),
                        row.getString("youtube_id"),
                        List.of(new Term(List.of(row.getString("heard")), row.getString("written")))))
                .list();
    }

    public boolean wasExtracted(String youtubeId) {
        return jdbc.sql("select count(*) from term_extraction where youtube_id = :id")
                        .param("id", youtubeId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    /**
     * Saves the terms extracted from one video together with what the model call cost, or neither.
     * A heard form the channel already has keeps its earlier written form.
     */
    @Transactional
    public void save(String youtubeId, String channel, List<Group> extracted, Reply reply, long millis) {
        List<Object[]> rows = new ArrayList<>();
        for (Group group : extracted) {
            for (Term term : group.terms()) {
                for (String heard : term.heard()) {
                    rows.add(new Object[] {channel, heard, term.write(), group.about(), youtubeId});
                }
            }
        }
        jdbcTemplate.batchUpdate(
                """
                insert into channel_term (channel, heard, written, about, youtube_id) values (?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                rows);
        jdbc.sql("""
                        insert into term_extraction (youtube_id, input_tokens, output_tokens, millis)
                        values (:id, :input, :output, :millis)
                        """)
                .param("id", youtubeId)
                .param("input", reply.inputTokens())
                .param("output", reply.outputTokens())
                .param("millis", millis)
                .update();
    }
}
