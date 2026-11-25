package io.github.sjh9714.subtitles.video;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VideoRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    VideoRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Claims a video for translation. Returns false when another request already holds it or it is done,
     * so two requests for the same video start only one job. A failed video can be claimed again, and
     * the chunks it already saved are kept.
     */
    public boolean claim(String youtubeId, String channel) {
        return jdbc.sql("""
                        insert into video (youtube_id, channel, status) values (:id, :channel, 'PENDING')
                        on conflict (youtube_id) do update
                            set status = 'PENDING', error = null, created_at = now(), finished_at = null
                            where video.status = 'FAILED'
                        """)
                .param("id", youtubeId)
                .param("channel", channel)
                .update() == 1;
    }

    public Optional<VideoStatus> findStatus(String youtubeId) {
        return jdbc.sql("select status from video where youtube_id = :id")
                .param("id", youtubeId)
                .query(String.class)
                .optional()
                .map(VideoStatus::valueOf);
    }

    /** The stored subtitles in video order, without the chunks the caller says it already has. */
    public List<Subtitle> findSubtitles(String youtubeId, Set<Integer> have) {
        return jdbc.sql("select chunk_index, seq, start_ms, end_ms, text from subtitle where youtube_id = :id"
                        + (have.isEmpty() ? "" : " and chunk_index not in (:have)")
                        + " order by chunk_index, seq")
                .param("id", youtubeId)
                .param("have", have)
                .query((row, n) -> new Subtitle(
                        row.getInt("chunk_index"),
                        row.getInt("seq"),
                        row.getLong("start_ms"),
                        row.getLong("end_ms"),
                        row.getString("text")))
                .list();
    }

    /** The chunks of this video that are already translated and saved. */
    public Set<Integer> findSavedChunks(String youtubeId) {
        return jdbc.sql("select chunk_index from translation_chunk where youtube_id = :id")
                .param("id", youtubeId)
                .query(Integer.class)
                .set();
    }

    /** Saves one chunk's subtitles together with what its model calls cost, or neither. */
    @Transactional
    public void saveChunk(String youtubeId, List<Subtitle> subtitles, ChunkUsage usage) {
        jdbcTemplate.batchUpdate(
                "insert into subtitle (youtube_id, chunk_index, seq, start_ms, end_ms, text) values (?, ?, ?, ?, ?, ?)",
                subtitles.stream()
                        .map(s -> new Object[] {youtubeId, s.chunk(), s.seq(), s.startMs(), s.endMs(), s.text()})
                        .toList());
        jdbc.sql("""
                        insert into translation_chunk (youtube_id, chunk_index, input_tokens, output_tokens, millis)
                        values (:id, :chunk, :input, :output, :millis)
                        """)
                .param("id", youtubeId)
                .param("chunk", usage.chunkIndex())
                .param("input", usage.inputTokens())
                .param("output", usage.outputTokens())
                .param("millis", usage.millis())
                .update();
    }

    public void markDone(String youtubeId) {
        jdbc.sql("update video set status = 'DONE', finished_at = now() where youtube_id = :id")
                .param("id", youtubeId)
                .update();
    }

    public void markFailed(String youtubeId, String error) {
        jdbc.sql("update video set status = 'FAILED', error = :error, finished_at = now() where youtube_id = :id")
                .param("id", youtubeId)
                .param("error", error)
                .update();
    }

    /**
     * Fails every video still marked as in progress. Called at startup, when no job can be running,
     * so a video cut off by a restart can be requested again instead of staying in progress forever.
     */
    public void failInterrupted() {
        jdbc.sql("""
                        update video set status = 'FAILED', error = 'interrupted by a restart', finished_at = now()
                        where status = 'PENDING'
                        """)
                .update();
    }

    public record ChunkUsage(int chunkIndex, long inputTokens, long outputTokens, long millis) {}
}
