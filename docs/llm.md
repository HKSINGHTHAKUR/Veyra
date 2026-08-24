# LLM / AI Summary Engine (Phase 5)

## Architecture
```
AiSummaryService → LlmClient → OpenAI
       ↑              ↑
  AiSummaryEventListener (AFTER_COMMIT)   FakeLlmClient (test profile)
```
- `LlmClient` interface isolates business logic from OpenAI SDK. `OpenAiLlmClient` (`@Profile("!test")`) uses `RestTemplate` with timeout; `FakeLlmClient` (`@Profile("test")`, `@Primary`) is deterministic, configurable per test (`Mode` enum). No direct OpenAI calls from controllers or booking transactions.

## Pre-visit Flow
1. `AppointmentService.confirmHold` commits `BOOKED` appointment, publishes `AppointmentConfirmedEvent`.
2. `AiSummaryEventListener` (`@TransactionalEventListener(AFTER_COMMIT)`) calls `AiSummaryService.generatePreVisit(appointmentId)` in new transaction (failure does not rollback booking).
3. Input: `symptomRepository` (appointment or slotHold), `doctor.specialisation`, appointmentId. Only minimal clinical context sent – never password/JWT/IDs.
4. Prompt `PRE_VISIT_V1` (`AiPromptTemplates.preVisitPrompt`, also `resources/prompts/pre_visit_v1.txt`) instructs: summarize symptoms, urgency ∈ [LOW,MEDIUM,HIGH], chiefComplaint, exactly 3 questions, no diagnosis, JSON only.
5. `LlmClient.generatePreVisit` returns `PreVisitResult{urgency, chiefComplaint, suggestedQuestions}`.
6. Validation: urgency must be `UrgencyLevel`, chiefComplaint non-empty, exactly 3 non-empty questions.
7. Persist `AiSummary(type=PRE_VISIT, status=COMPLETED, urgency, chiefComplaint, suggestedQuestions as JSON, modelUsed)`. On validation/timeout/exception → `status=FAILED, failureReason` (truncated, no stack).

## Post-visit Flow
1. `ConsultationService.createConsultation` (doctor creates `Consultation` + `PrescriptionItem`s) publishes `ConsultationCompletedEvent`.
2. Listener calls `generatePostVisit`, which loads `Consultation` + prescriptions, builds `PostVisitInput{clinicalNotes, followUpInstructions, prescriptions}`.
3. Prompt `POST_VISIT_V1` instructs: patient-friendly summary from notes + prescriptions, preserve prescription facts, no invented meds, JSON only.
4. `generatePostVisit` returns `PostVisitResult{summary, medications, followUpSteps}`.
5. Validation: summary non-empty, followUpSteps non-empty, medications must match DB (if DB has 0, LLM must return 0; else count must match; if mismatch → FAILED). On success, persist `summaryText`, `medications` (from DB, authoritative), `followUpSteps` as JSON.

## Structured Output
Pre-visit JSON:
```json
{"urgency":"MEDIUM","chiefComplaint":"...","suggestedQuestions":["Q1","Q2","Q3"]}
```
Post-visit JSON:
```json
{"summary":"...","medications":[{"name":"Amoxicillin","dosage":"500 mg","frequency":"Twice daily","duration":"5 days"}],"followUpSteps":["Take as prescribed","Return in 1 week"]}
```

## Prompts & Versioning
- `AiPromptTemplates.PRE_VISIT_V1` / `POST_VISIT_V1` constants, also `src/main/resources/prompts/*.txt`.
- Version identifier stored via `modelUsed` (default `gpt-4o-mini` from `app.ai.openai.model`).

## Failure Handling & Timeouts
- LLM failures (exception, timeout `SocketTimeoutException`, malformed JSON) are caught, logged at WARN (no sensitive data), and `AiSummary` is marked `FAILED` – appointment/consultation remain `BOOKED`/`COMPLETED`.
- Never `Appointment + OpenAI` in one transaction; AI runs `AFTER_COMMIT`.
- Timeout via `app.ai.openai.timeout-seconds` (5 test, 30 prod); `OpenAiLlmClient` respects it (RestTemplate future: HttpComponentsClientHttpRequestFactory).
- No aggressive retries; bounded retry may be added via background scheduler later; currently `FAILED` persists and can be regenerated (duplicate logic allows retry).

## Duplicate Prevention
Unique constraint `uq_ai_appointment_type (appointment_id, type)`; service checks:
- `COMPLETED` → return existing, no duplicate
- `PENDING` → return existing
- `FAILED` → regenerate by reusing row (update, not insert)

## Privacy
Only `symptoms`, `clinicalNotes`, `prescriptions`, `specialisation` sent. Never password/JWT/security. No sensitive logging at INFO; WARN only logs appointmentId + truncated failureReason.

## Testing Strategy
- `FakeLlmClient` modes: `SUCCESS`, `TIMEOUT`, `EXCEPTION`, `INVALID_URGENCY`, `MISSING_CHIEF`, `WRONG_QUESTION_COUNT`, `INVALID_POST`, `EXCEPTION_POST`.
- Tests run without `OPENAI_API_KEY` (`application-test.yml` has `test-key`, timeout 5s).
- Coverage: pre-visit 1-9 (valid, urgency, chief, 3 questions, timeout, exception), post-visit 10-15 (valid, summary, prescription, follow-up, failure, consultation remains), duplicates 16-17, failure isolation (appointment booked / consultation completed remain), ownership (patient/doctor/admin).

## Configuration
`application.yml`: `app.ai.openai.api-key`, `base-url`, `model`, `timeout-seconds` (env `OPENAI_API_KEY` etc.). `application-test.yml` uses `test-key`. `FakeLlmClient` is `@Primary` for `test` profile.

## Endpoints
`GET /api/appointments/{id}/ai/pre-visit`, `GET /{id}/ai/post-visit` (owner patient/doctor/admin, `403` otherwise). Manual triggers `POST /{id}/ai/pre-visit/generate` (ADMIN/PATIENT) and `POST /{id}/ai/post-visit/generate` (DOCTOR/ADMIN) for retry.

## Database
`ai_summaries` (V1) + V3 adds `medications`, `follow_up_steps` TEXT columns. No schema change for pre-visit. No raw provider response stored.

## Known Limitations
- No streaming; simple request/response.
- No prompt management system beyond version constants.
- No background retry scheduler yet (failed summaries remain `FAILED` until manual regenerate).
