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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@Slf4j
@Service
@RequiredArgsConstructor
class UploadService {
    private final CloudflareConfig conf;
    private final RestTemplate restTemplate;

    @Value("${public-video-url}")
    private String videoUrl;

    @PostConstruct
    void runVideUpload() {
        uploadVideo(videoUrl);
    }

    void uploadVideo(String publicVideoUrl) {
        log.info("token: {} id: {}", conf.getAccountToken(), conf.getAccountId());

        String cloudflareUploadStreamUrl = conf.getApiUrl() + "/accounts/" + conf.getAccountId() + "/stream/copy";

        RequestCallback setRequestHeaders = req -> req.getHeaders().setAccept(List.of(APPLICATION_OCTET_STREAM, ALL));

        ResponseExtractor<?> uploadVideoToCloudflare = response -> {
            TusClient tusClient = createNewTusClient(conf.getAccountToken(), cloudflareUploadStreamUrl);
            InputStream responseInputStream = response.getBody();
            TusUpload tusUpload = createNewTusUpload(responseInputStream);

            TusExecutor tusExecutor = createNewTusExecutor(tusClient, tusUpload);

            try {
                log.info("Starting video upload from {} to Cloudflare", publicVideoUrl);
                tusExecutor.makeAttempts();
            } catch (ProtocolException e) {
                log.error("Video upload error: message: {}. Stacktrace:", e.getLocalizedMessage(), e);
            }

            return null;
        };

        try {
            restTemplate.execute(publicVideoUrl, GET, setRequestHeaders, uploadVideoToCloudflare);
        } catch (RestClientException e) {
            log.error("Video download error: message: {}. Stacktrace:", e.getLocalizedMessage(), e.getCause());
        }
    }

    private static TusClient createNewTusClient(String cloudflareApiToken,
                                                String cloudflareUploadStreamUrl) throws MalformedURLException {
        // see https://github.com/tus/tus-java-client#usage
        TusClient client = new TusClient();
        client.setUploadCreationURL(new URL(cloudflareUploadStreamUrl));
        client.setHeaders(Collections.singletonMap(HttpHeaders.AUTHORIZATION, "Bearer " + cloudflareApiToken));
        client.enableResuming(new TusURLMemoryStore());

        return client;
    }

    private static TusUpload createNewTusUpload(InputStream inputStream) {
        TusUpload upload = new TusUpload();
        upload.setInputStream(inputStream);
        return upload;
    }

    private static TusExecutor createNewTusExecutor(TusClient client, TusUpload upload) {
        return new TusExecutor() {
            @Override
            protected void makeAttempt() throws ProtocolException, IOException {
                TusUploader uploader = client.resumeOrCreateUpload(upload);
                // Upload the file in chunks of 1KB sizes.
                uploader.setChunkSize(1024);
                do {
                    long totalBytes = upload.getSize();
                    long bytesUploaded = uploader.getOffset();
                    double progress = (double) bytesUploaded / totalBytes * 100;

                    log.info("Upload at {}%.", progress);
                } while (uploader.uploadChunk() > -1);

                // Allow the HTTP connection to be closed and cleaned up
                uploader.finish();

                log.info("Upload finished");
                log.info("Upload available at: {}", uploader.getUploadURL().toString());
            }
        };
    }
}
