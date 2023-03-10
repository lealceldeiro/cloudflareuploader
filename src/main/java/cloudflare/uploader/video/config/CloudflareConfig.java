package cloudflare.uploader.video.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Cloudflare related configuration properties.
 */
@Getter
@ToString
@Configuration
@Setter(AccessLevel.PACKAGE)
@ConfigurationProperties(prefix = "cloudflare")
public class CloudflareConfig {
    @Getter(AccessLevel.NONE)
    private boolean enabled;
    private String apiUrl;
    private String accountToken;
    private String accountId;

    public boolean isCfEnabled() {
        return enabled;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(15))
                                        .setReadTimeout(Duration.ofSeconds(15))
                                        .build();
    }
}
