package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.caption.CaptionEvent;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/videos/{youtubeId}")
class VideoController {

    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final VideoRepository videos;
    private final TranslationJob job;

    VideoController(VideoRepository videos, TranslationJob job) {
        this.videos = videos;
        this.job = job;
    }

    /**
     * Starts a translation unless this video is already stored or in progress.
     * The position is where the viewer is watching, which is translated first.
     */
    @PostMapping("/translation")
    ResponseEntity<StatusResponse> requestTranslation(
            @PathVariable String youtubeId,
            @RequestParam(defaultValue = "0") long positionMs,
            @RequestBody TranslationRequest request) {
        if (!YOUTUBE_ID.matcher(youtubeId).matches()
                || request.channel() == null
                || request.events() == null
                || request.events().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (videos.claim(youtubeId, request.channel())) {
            job.run(youtubeId, new VideoInfo(request.title(), request.channel()), request.events(), positionMs);
            return ResponseEntity.accepted().body(new StatusResponse(VideoStatus.PENDING));
        }
        return ResponseEntity.ok(new StatusResponse(videos.findStatus(youtubeId).orElseThrow()));
    }

    /** Returns what is translated so far, and moves a running translation to the viewer's position. */
    @GetMapping("/subtitles")
    SubtitlesResponse subtitles(
            @PathVariable String youtubeId, @RequestParam(defaultValue = "0") long positionMs) {
        VideoStatus status = videos.findStatus(youtubeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean ready = status == VideoStatus.DONE
                || (status == VideoStatus.PENDING && job.watch(youtubeId, positionMs));
        return new SubtitlesResponse(status, ready, videos.findSubtitles(youtubeId));
    }

    record TranslationRequest(String title, String channel, List<CaptionEvent> events) {}

    record StatusResponse(VideoStatus status) {}

    /** {@code ready} says whether the viewer's position is translated, so an empty screen there means silence. */
    record SubtitlesResponse(VideoStatus status, boolean ready, List<Subtitle> subtitles) {}
}
