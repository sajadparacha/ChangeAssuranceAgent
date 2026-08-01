package com.company.changeassurance.application.port.out;

import com.company.changeassurance.domain.model.AiTaskType;

/**
 * Port for controlled AI reasoning tasks. Implementations must be interchangeable (LSP).
 *
 * <p>Providers: Fake (tests), Disabled (AI off), Spring AI (Phase 4).
 */
public interface ModelGateway {

    /**
     * @return {@code true} when the gateway can serve AI tasks
     */
    boolean isAvailable();

    /**
     * Executes a typed AI task and returns a validated structured response.
     *
     * @param taskType     controlled task enum
     * @param request      framework-free request envelope
     * @param responseType expected response class
     * @param <T>          response type
     * @return structured response instance
     */
    <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType);

    /**
     * Untrusted content and task metadata for a model call. System instructions are owned by adapters.
     */
    record AiRequest(
            String reviewId,
            String promptVersion,
            String untrustedContent,
            int maxInputChars,
            int maxOutputChars
    ) {
        public AiRequest {
            if (reviewId == null || reviewId.isBlank()) {
                throw new IllegalArgumentException("reviewId must not be blank");
            }
            if (promptVersion == null || promptVersion.isBlank()) {
                throw new IllegalArgumentException("promptVersion must not be blank");
            }
            if (maxInputChars < 1) {
                throw new IllegalArgumentException("maxInputChars must be >= 1");
            }
            if (maxOutputChars < 1) {
                throw new IllegalArgumentException("maxOutputChars must be >= 1");
            }
        }
    }
}
