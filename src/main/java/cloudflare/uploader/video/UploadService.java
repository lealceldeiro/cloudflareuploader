package cloudflare.uploader.video;

import cloudflare.uploader.video.config.CloudflareConfig;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@Slf4j
@Service
@RequiredArgsConstructor
class UploadService {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private final CloudflareConfig conf;
    private final RestTemplate restTemplate;

    @Value("${public-video-url}")
    private String videoUrl;

    @Value("${public-upload-url}")
    private String publicUploadUrl;

    @PostConstruct
    void runVideUpload() {
        EXECUTOR.schedule(() -> uploadVideo(videoUrl), 3, SECONDS);
    }

    void uploadVideo(String publicVideoUrl) {
        if (publicVideoUrl == null) {
            return;
        }
        log.info("token: {} id: {}", conf.getAccountToken(), conf.getAccountId());

        String uploadUrl = conf.isEnabled() ? conf.getApiUrl() + "/accounts/" + conf.getAccountId() + "/stream/copy"
                                            : publicUploadUrl;
        RequestCallback setRequestHeaders = req -> req.getHeaders().setAccept(List.of(APPLICATION_OCTET_STREAM, ALL));
        ResponseExtractor<?> uploadVideo = response -> attemptVideoUpload(publicVideoUrl, uploadUrl, response);

        try {
            restTemplate.execute(publicVideoUrl, GET, setRequestHeaders, uploadVideo);
        } catch (RestClientException e) {
            log.error("Video download error: message: {}. Stacktrace:", e.getLocalizedMessage(), e.getCause());
        }
    }

    private Object attemptVideoUpload(String videoUrl, String uploadUrl, ClientHttpResponse res) throws IOException {
        File tempFile = temporaryFileFromResponseInputStream(videoUrl, res);

        TusClient tusClient = createNewTusClient(conf.getAccountToken(), uploadUrl);
        TusUpload tusUpload = createNewTusUpload(tempFile);
        TusExecutor tusExecutor = createNewTusExecutor(tusClient, tusUpload);
        try {
            log.info("Starting video upload from {} to {}", videoUrl, uploadUrl);
            tusExecutor.makeAttempts();
        } catch (ProtocolException e) {
            log.error("Video upload error: message: {}. Stacktrace:", e.getLocalizedMessage(), e);
        }
        return null;
    }

    private static File temporaryFileFromResponseInputStream(String videoUrl, ClientHttpResponse res) throws IOException {
        String fileName = videoNameFromUrl(videoUrl);
        log.info("Video name: {}",fileName);

        File tempFile = new File(fileName);
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            StreamUtils.copy(res.getBody(), fos);
            return tempFile;
        }
    }

    private static String videoNameFromUrl(String videoUrl) {
        String videoName = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
        int extensionSeparator = videoName.lastIndexOf(".");
        String videoExtension = extensionSeparator != -1 ? videoName.substring(extensionSeparator) : ".mp4";

        return videoName.substring(0, extensionSeparator) + "_" + System.currentTimeMillis() + videoExtension;
    }

    private static TusClient createNewTusClient(String uploadApiToken, String uploadUrl) throws MalformedURLException {
        // see https://github.com/tus/tus-java-client#usage
        TusClient client = new TusClient();
        client.setUploadCreationURL(new URL(uploadUrl));
        client.enableResuming(new TusURLMemoryStore());
        Optional.ofNullable(uploadApiToken)
                .ifPresent(token -> client.setHeaders(singletonMap(AUTHORIZATION, "Bearer " + token)));

        return client;
    }

    private static TusUpload createNewTusUpload(File file) {
        TusUpload upload;
        try {
            upload = new TusUpload(file);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        upload.setMetadata(singletonMap("name", file.getName()));
        return upload;
    }

    private static TusExecutor createNewTusExecutor(TusClient client, TusUpload upload) {
        return new TusExecutor() {
            @Override
            protected void makeAttempt() throws ProtocolException, IOException {
                TusUploader uploader = client.resumeOrCreateUpload(upload);
                // min size: https://developers.cloudflare.com/stream/uploading-videos/upload-video-file/#what-is-tus
                uploader.setChunkSize(5_242_880);
                while (uploader.uploadChunk() > -1) {
                    // noop
                }

                // Allow the HTTP connection to be closed and cleaned up
                uploader.finish();

                log.info("Upload finished; available at: {}", uploader.getUploadURL().toString());
            }
        };
    }
}
