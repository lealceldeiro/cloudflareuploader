package cloudflare.uploader.video;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
public final class Utils {
    private Utils() {
        // avoid instantiation
    }
    public static File tempFileFromResponseInputStream(String videoUrl, ClientHttpResponse res) throws IOException {
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
        int extensionSeparatorPost = videoName.lastIndexOf(".");
        String videoExtension = extensionSeparatorPost != -1 ? videoName.substring(extensionSeparatorPost) : ".mp4";

        return videoName.substring(0, extensionSeparatorPost) + "_" + System.currentTimeMillis() + videoExtension;
    }
}
