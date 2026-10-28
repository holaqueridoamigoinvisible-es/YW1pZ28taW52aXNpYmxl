package com.dgc.amigoinvisible.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "amigo")
public class AmigoProperties {

    private Participants participants = new Participants();
    private Exclusions exclusions = new Exclusions();
    private Mail mail = new Mail();
    private Token token = new Token();
    private App app = new App();

    @Getter
    @Setter
    public static class Participants {
        /** Path to the CSV file with participants (name,email). */
        private String file = "classpath:participants.csv";
    }

    @Getter
    @Setter
    public static class Exclusions {
        /** Path to the CSV file with forbidden sender→receiver pairs. */
        private String file = "classpath:exclusions.csv";
    }

    @Getter
    @Setter
    public static class Mail {
        private String from;
        private String fromName = "Amigo Invisible";
    }

    @Getter
    @Setter
    public static class Token {
        /** Secret used to derive the 16-byte AES key (MD5 hash). */
        private String secret = "amigo-invisible-secret-key-padding";
    }

    @Getter
    @Setter
    public static class App {
        /** Public base URL used to construct reveal links (no trailing slash). */
        private String baseUrl = "http://localhost:8080";
    }
}
