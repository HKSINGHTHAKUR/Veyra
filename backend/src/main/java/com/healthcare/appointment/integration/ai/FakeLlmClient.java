package com.healthcare.appointment.integration.ai;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fake LLM for tests (profile test). Deterministic, no network.
 * Behaviour is configurable per test via setters. By default returns valid results.
 */
@Component
@Profile("test")
@Primary
public class FakeLlmClient implements LlmClient {

    public enum Mode { SUCCESS, TIMEOUT, EXCEPTION, INVALID_URGENCY, MISSING_CHIEF, WRONG_QUESTION_COUNT, INVALID_POST, EXCEPTION_POST }

    private final AtomicReference<Mode> preVisitMode = new AtomicReference<>(Mode.SUCCESS);
    private final AtomicReference<Mode> postVisitMode = new AtomicReference<>(Mode.SUCCESS);

    public void setPreVisitMode(Mode m) { preVisitMode.set(m); }
    public void setPostVisitMode(Mode m) { postVisitMode.set(m); }
    public void reset() { preVisitMode.set(Mode.SUCCESS); postVisitMode.set(Mode.SUCCESS); }

    @Override
    public PreVisitResult generatePreVisit(PreVisitInput input) throws LlmException {
        Mode m = preVisitMode.get();
        return switch (m) {
            case SUCCESS -> new PreVisitResult("MEDIUM", "Persistent headache and fatigue", List.of("When did symptoms begin?", "Have they changed?", "Any other symptoms?"));
            case TIMEOUT -> throw new LlmException("timeout", new java.net.SocketTimeoutException("fake timeout"));
            case EXCEPTION -> throw new LlmException("fake provider failure");
            case INVALID_URGENCY -> new PreVisitResult("VERY URGENT", "Headache", List.of("Q1","Q2","Q3"));
            case MISSING_CHIEF -> new PreVisitResult("LOW", "", List.of("Q1","Q2","Q3"));
            case WRONG_QUESTION_COUNT -> new PreVisitResult("HIGH", "Complaint", List.of("Only one"));
            default -> throw new LlmException("unsupported mode");
        };
    }

    @Override
    public PostVisitResult generatePostVisit(PostVisitInput input) throws LlmException {
        Mode m = postVisitMode.get();
        return switch (m) {
            case SUCCESS, INVALID_URGENCY, MISSING_CHIEF, WRONG_QUESTION_COUNT -> {
                // For post-visit, map prescriptions faithfully
                List<PostVisitResult.Medication> meds = input.getPrescriptions() == null ? List.of() :
                        input.getPrescriptions().stream().map(p ->
                                new PostVisitResult.Medication(p.getMedicationName(), p.getDosage(), p.getFrequency(),
                                        p.getDurationDays() != null ? p.getDurationDays() + " days" : "")).toList();
                yield new PostVisitResult("Your consultation focused on " + input.getClinicalNotes(), meds, List.of("Take medication as prescribed", "Follow up in one week"));
            }
            case EXCEPTION_POST, EXCEPTION -> throw new LlmException("fake post failure");
            case INVALID_POST -> new PostVisitResult("", List.of(), List.of());
            case TIMEOUT -> throw new LlmException("timeout", new java.net.SocketTimeoutException("fake timeout post"));
            default -> throw new LlmException("unsupported");
        };
    }
}
