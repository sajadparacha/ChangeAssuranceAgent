package com.company.changeassurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Change Assurance Agent — standalone Spring Boot entry point.
 *
 * <p>Runnable as an executable WAR ({@code java -jar}) or deployed to an external
 * servlet container such as WebLogic 15.x via {@link ChangeAssuranceServletInitializer}.
 */
@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class
})
public class ChangeAssuranceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChangeAssuranceApplication.class, args);
    }
}
