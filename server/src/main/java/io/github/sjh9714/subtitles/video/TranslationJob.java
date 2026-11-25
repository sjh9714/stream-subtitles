package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.caption.CaptionEvent;
import io.github.sjh9714.subtitles.caption.CaptionParser;
import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.caption.SentenceRegrouper;
import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Chunker;
import io.github.sjh9714.subtitles.translation.Glossary;
import io.github.sjh9714.subtitles.translation.Glossary.Group;
import io.github.sjh9714.subtitles.translation.TermExtractor;
import io.github.sjh9714.subtitles.translation.TermsReply;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
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
 *
 * <p>While the chunks are translated, a second model reads a part of the captions and lists the names and
 * terms that need a fixed written form. Translation does not wait for it. The chunks that start after it
 * finishes use the new terms, and so does every later video of the channel.
 */
@Component
class TranslationJob {

    private static final Logger log = LoggerFactory.getLogger(TranslationJob.class);
    private static final int MAX_ATTEMPTS = 3;
    /** How many chunks of one video are with the model at the same time. */
    private static final int PARALLEL_CALLS = 4;
    /** How many chunks the term extractor reads, so its cost does not grow with the video's length. */
    private static final int SAMPLE_CHUNKS = 6;

    private final Translator translator;
    private final VideoRepository videos;
    private final Glossary glossary;
    private final TermExtractor extractor;
    private final ChannelTermRepository channelTerms;
    private final Map<String, ChunkQueue> running = new ConcurrentHashMap<>();

    TranslationJob(
            Translator translator,
            VideoRepository videos,
            Glossary glossary,
            TermExtractor extractor,
            ChannelTermRepository channelTerms) {
        this.translator = translator;
        this.videos = videos;
        this.glossary = glossary;
        this.extractor = extractor;
        this.channelTerms = channelTerms;
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
            List<Group> known = Stream.concat(
                            glossary.groupsFor(title, channel, sentences).stream(), channelTerms.find(channel).stream())
                    .toList();
            AtomicReference<VideoInfo> video =
                    new AtomicReference<>(new VideoInfo(title, channel, Glossary.firstWins(known)));
            List<Chunk> chunks = Chunker.split(sentences);
            Set<Integer> saved = videos.findSavedChunks(youtubeId);
            ChunkQueue queue = new ChunkQueue(
                    chunks.stream().filter(chunk -> !saved.contains(chunk.index())).toList(), positionMs);
            running.put(youtubeId, queue);
            // Closing the workers waits for the extraction too, so a finished video has its terms stored.
            try (ExecutorService workers = Executors.newFixedThreadPool(PARALLEL_CALLS + 1)) {
                workers.execute(() -> extractTerms(youtubeId, video, sample(chunks)));
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
    private void translateWaiting(String youtubeId, AtomicReference<VideoInfo> video, ChunkQueue queue) {
        for (Chunk chunk = queue.next(); chunk != null; chunk = queue.next()) {
            translate(youtubeId, video.get(), chunk);
            queue.finished(chunk);
        }
    }

    /** A few chunks spread evenly over the video. */
    private static List<Sentence> sample(List<Chunk> chunks) {
        int count = Math.min(chunks.size(), SAMPLE_CHUNKS);
        return IntStream.range(0, count)
                .boxed()
                .flatMap(i -> chunks.get(i * chunks.size() / count).sentences().stream())
                .toList();
    }

    /**
     * Adds the terms found in the sample to what the video's remaining chunks are translated with, then
     * stores them for the channel. A failure here leaves the translation as it is.
     */
    private void extractTerms(String youtubeId, AtomicReference<VideoInfo> video, List<Sentence> sample) {
        try {
            if (sample.isEmpty() || channelTerms.wasExtracted(youtubeId)) {
                return;
            }
            long startedAt = System.currentTimeMillis();
            VideoInfo before = video.get();
            Reply reply = extractor.extract(before, sample);
            List<Group> extracted = TermsReply.parse(reply.text(), before.channel(), youtubeId);
            video.set(new VideoInfo(
                    before.title(),
                    before.channel(),
                    Glossary.firstWins(Stream.concat(before.glossary().stream(), extracted.stream()).toList())));
            channelTerms.save(youtubeId, before.channel(), extracted, reply, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            log.warn("could not extract terms from video {}", youtubeId, e);
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
