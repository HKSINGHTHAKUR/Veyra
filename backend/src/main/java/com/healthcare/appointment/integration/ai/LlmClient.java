package com.healthcare.appointment.integration.ai;

/**
 * Abstraction for LLM provider. Business logic depends on this interface,
 * not directly on OpenAI SDK. Allows Fake implementation for tests.
 */
public interface LlmClient {

    PreVisitResult generatePreVisit(PreVisitInput input) throws LlmException;

    PostVisitResult generatePostVisit(PostVisitInput input) throws LlmException;
}
