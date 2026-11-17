package io.github.sjh9714.subtitles.video;

import java.util.List;
import java.util.Optional;
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
     * so two requests for the same video start only one job. A failed video can be claimed again.
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

    public List<Subtitle> findSubtitles(String youtubeId) {
        return jdbc.sql("select seq, start_ms, end_ms, text from subtitle where youtube_id = :id order by seq")
                .param("id", youtubeId)
                .query((row, n) -> new Subtitle(
                        row.getInt("seq"), row.getLong("start_ms"), row.getLong("end_ms"), row.getString("text")))
                .list();
    }

    @Transactional
    public void saveResult(String youtubeId, List<Subtitle> subtitles, List<ChunkUsage> usages) {
        jdbc.sql("delete from subtitle where youtube_id = :id").param("id", youtubeId).update();
        jdbc.sql("delete from translation_chunk where youtube_id = :id").param("id", youtubeId).update();
        jdbcTemplate.batchUpdate(
                "insert into subtitle (youtube_id, seq, start_ms, end_ms, text) values (?, ?, ?, ?, ?)",
                subtitles.stream()
                        .map(s -> new Object[] {youtubeId, s.seq(), s.startMs(), s.endMs(), s.text()})
                        .toList());
        jdbcTemplate.batchUpdate(
                "insert into translation_chunk (youtube_id, chunk_index, input_tokens, output_tokens, millis)"
                        + " values (?, ?, ?, ?, ?)",
                usages.stream()
                        .map(u -> new Object[] {youtubeId, u.chunkIndex(), u.inputTokens(), u.outputTokens(), u.millis()})
                        .toList());
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

    public record ChunkUsage(int chunkIndex, long inputTokens, long outputTokens, long millis) {}
}
