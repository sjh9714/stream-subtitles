package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.caption.CaptionEvent;
import io.github.sjh9714.subtitles.caption.CaptionParser;
import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.caption.SentenceRegrouper;
import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Chunker;
import io.github.sjh9714.subtitles.translation.Glossary;
import io.github.sjh9714.subtitles.translation.Translator;
import io.github.sjh9714.subtitles.translation.TranslationReply;
import io.github.sjh9714.subtitles.translation.TranslationReply.MalformedReplyException;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import io.github.sjh9714.subtitles.video.VideoRepository.ChunkUsage;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Turns one video's captions into stored subtitles, off the request thread.
 *
 * <p>Each chunk is saved as soon as it is translated, so the viewer sees it without waiting for the rest
 * and a later attempt does not pay for it again. Chunks are taken from where the viewer is watching
 * onward, a few at a time. A chunk whose reply skips or repeats a line is asked again. A chunk that still
 * fails then fails the video once the other chunks are saved, and asking for the video again translates
 * only what is missing. The English captions are not stored, so that needs the captions sent again.
 */
@Component
class TranslationJob {

    private static final Logger log = LoggerFactory.getLogger(TranslationJob.class);
    private static final int MAX_ATTEMPTS = 3;
    /** How many chunks of one video are with the model at the same time. */
    private static final int PARALLEL_CALLS = 4;

    private final Translator translator;
    private final VideoRepository videos;
    private final Glossary glossary;
    private final Map<String, ChunkQueue> running = new ConcurrentHashMap<>();

    TranslationJob(Translator translator, VideoRepository videos, Glossary glossary) {
        this.translator = translator;
        this.videos = videos;
        this.glossary = glossary;
    }

    /** Jobs live in this process only, so whatever was in progress before it started was cut off. */
    @PostConstruct
    public void failInterrupted() {
        videos.failInterrupted();
    }

    @Async
    public void run(String youtubeId, String title, String channel, List<CaptionEvent> events, long positionMs) {
        try {
            List<Sentence> sentences = SentenceRegrouper.regroup(CaptionParser.parse(events));
            VideoInfo video = new VideoInfo(title, channel, glossary.groupsFor(title, channel, sentences));
            Set<Integer> saved = videos.findSavedChunks(youtubeId);
            ChunkQueue queue = new ChunkQueue(
                    Chunker.split(sentences).stream().filter(chunk -> !saved.contains(chunk.index())).toList(),
                    positionMs);
            running.put(youtubeId, queue);
            try (ExecutorService workers = Executors.newFixedThreadPool(PARALLEL_CALLS)) {
                CompletableFuture.allOf(Stream.generate(() -> CompletableFuture.runAsync(
                                        () -> translateWaiting(youtubeId, video, queue), workers))
                                .limit(PARALLEL_CALLS)
                                .toArray(CompletableFuture[]::new))
                        .join();
            }
            videos.markDone(youtubeId);
        } catch (RuntimeException e) {
            log.error("translation failed for video {}", youtubeId, e);
            videos.markFailed(youtubeId, String.valueOf(e.getMessage()));
        } finally {
            running.remove(youtubeId);
        }
    }

    /**
     * Tells a running job where the viewer is now, so it translates that place next.
     * Returns whether that place is already translated.
     */
    public boolean watch(String youtubeId, long positionMs) {
        ChunkQueue queue = running.get(youtubeId);
        return queue != null && queue.watch(positionMs);
    }

    /** One worker. It stops at its first failure and leaves the remaining chunks to the other workers. */
    private void translateWaiting(String youtubeId, VideoInfo video, ChunkQueue queue) {
        for (Chunk chunk = queue.next(); chunk != null; chunk = queue.next()) {
            translate(youtubeId, video, chunk);
            queue.finished(chunk);
        }
    }

    private void translate(String youtubeId, VideoInfo video, Chunk chunk) {
        long startedAt = System.currentTimeMillis();
        long inputTokens = 0;
        long outputTokens = 0;
        List<String> lines = null;
        for (int attempt = 1; lines == null; attempt++) {
            Reply reply = translator.translate(video, chunk);
            inputTokens += reply.inputTokens();
            outputTokens += reply.outputTokens();
            try {
                lines = TranslationReply.parse(reply.text(), chunk.sentences().size());
            } catch (MalformedReplyException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("asking again for chunk {} of video {}: {}", chunk.index(), youtubeId, e.getMessage());
            }
        }
        List<Subtitle> subtitles = new ArrayList<>();
        for (int i = 0; i < chunk.sentences().size(); i++) {
            String korean = lines.get(i);
            if (!korean.isBlank()) {
                Sentence sentence = chunk.sentences().get(i);
                subtitles.add(new Subtitle(i, sentence.startMs(), sentence.endMs(), korean));
            }
        }
        videos.saveChunk(youtubeId, subtitles, new ChunkUsage(
                chunk.index(), inputTokens, outputTokens, System.currentTimeMillis() - startedAt));
    }
}
