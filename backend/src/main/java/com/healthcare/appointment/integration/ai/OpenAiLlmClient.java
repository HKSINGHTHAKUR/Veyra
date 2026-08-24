package com.healthcare.appointment.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Production OpenAI client. Only active when not in test profile.
 * Uses RestTemplate with timeout from properties. Parses structured JSON.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final ObjectMapper objectMapper;

    @Value("${app.ai.openai.api-key:}")
    private String apiKey;

    @Value("${app.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.openai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Override
    public PreVisitResult generatePreVisit(PreVisitInput input) throws LlmException {
        String prompt = AiPromptTemplates.preVisitPrompt(input);
        String json = callOpenAi(prompt);
        return parsePreVisit(json);
    }

    @Override
    public PostVisitResult generatePostVisit(PostVisitInput input) throws LlmException {
        String prompt = AiPromptTemplates.postVisitPrompt(input);
        String json = callOpenAi(prompt);
        return parsePostVisit(json);
    }

    private String callOpenAi(String prompt) throws LlmException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("OpenAI API key not configured");
        }
        try {
            RestTemplate rest = new RestTemplate();
            // timeout handled via simple approach; production could use HttpComponentsClientHttpRequestFactory
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            String body = objectMapper.writeValueAsString(new OpenAiRequest(model, List.of(new OpenAiRequest.Message("user", prompt)), 0.2));
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.exchange(baseUrl + "/chat/completions", HttpMethod.POST, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new LlmException("OpenAI non-2xx: " + resp.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) throw new LlmException("Empty content from OpenAI");
            // Extract JSON block if wrapped in markdown
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >=0 && end>start) content = content.substring(start, end+1);
            return content;
        } catch (LlmException e) { throw e; }
        catch (Exception e) {
            throw new LlmException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    private PreVisitResult parsePreVisit(String json) throws LlmException {
        try {
            JsonNode node = objectMapper.readTree(json);
            String urgency = node.path("urgency").asText(null);
            String chief = node.path("chiefComplaint").asText(null);
            JsonNode qs = node.path("suggestedQuestions");
            List<String> questions = new ArrayList<>();
            if (qs.isArray()) for (JsonNode q : qs) questions.add(q.asText());
            return new PreVisitResult(urgency, chief, questions);
        } catch (Exception e) {
            throw new LlmException("Failed to parse pre-visit JSON: " + e.getMessage(), e);
        }
    }

    private PostVisitResult parsePostVisit(String json) throws LlmException {
        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = node.path("summary").asText(null);
            List<PostVisitResult.Medication> meds = new ArrayList<>();
            JsonNode medArr = node.path("medications");
            if (medArr.isArray()) {
                for (JsonNode m : medArr) {
                    meds.add(new PostVisitResult.Medication(
                            m.path("name").asText(""), m.path("dosage").asText(""), m.path("frequency").asText(""), m.path("duration").asText("")));
                }
            }
            List<String> steps = new ArrayList<>();
            JsonNode sArr = node.path("followUpSteps");
            if (sArr.isArray()) for (JsonNode s : sArr) steps.add(s.asText());
            return new PostVisitResult(summary, meds, steps);
        } catch (Exception e) {
            throw new LlmException("Failed to parse post-visit JSON", e);
        }
    }

    private record OpenAiRequest(String model, java.util.List<Message> messages, double temperature) {
        public record Message(String role, String content) {}
    }
}
