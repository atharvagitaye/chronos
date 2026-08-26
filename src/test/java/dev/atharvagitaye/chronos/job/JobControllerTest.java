package dev.atharvagitaye.chronos.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OutboxRepository outboxRepository;

	@Test
	void createsAndRetrievesJob() throws Exception {
		String request = """
				{"jobType":"SIMULATED","payload":{"durationMs":100},"priority":"HIGH","maxRetries":2}
				""";

		String response = mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andReturn().getResponse().getContentAsString();

		String jobId = response.replaceAll(".*\\\"jobId\\\":\\\"([^\\\"]+)\\\".*", "$1");
		mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobType").value("SIMULATED"));
	}

	@Test
	void rejectsInvalidMaxRetries() throws Exception {
		mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"LOW\",\"maxRetries\":11}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void createsAnOutboxEventWithTheJob() throws Exception {
		mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"MEDIUM\"}"))
				.andExpect(status().isCreated());

		OutboxEvent event = outboxRepository.findAll().stream()
				.max(Comparator.comparing(OutboxEvent::getCreatedAt)).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
		org.junit.jupiter.api.Assertions.assertEquals("JOB_CREATED", event.getEventType());
		org.junit.jupiter.api.Assertions.assertEquals("MEDIUM", event.getPayload().get("priority"));
	}

	@Test
	void returnsTheOriginalJobForARepeatedIdempotentRequest() throws Exception {
		String request = "{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"HIGH\"}";
		String first = mockMvc.perform(post("/api/v1/jobs").header("Idempotency-Key", "request-1")
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();

		mockMvc.perform(post("/api/v1/jobs").header("Idempotency-Key", "request-1")
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.jobId").value(org.hamcrest.Matchers.containsString(
						first.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1"))));
	}

	@Test
	void rejectsAnIdempotencyKeyWithADifferentRequest() throws Exception {
		mockMvc.perform(post("/api/v1/jobs").header("Idempotency-Key", "request-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"HIGH\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/jobs").header("Idempotency-Key", "request-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobType\":\"SIMULATED\",\"payload\":{\"durationMs\":1},\"priority\":\"HIGH\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void cancelsJob() throws Exception {
		String request = "{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"LOW\"}";
		String response = mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String jobId = response.replaceAll(".*\\\"jobId\\\":\\\"([^\\\"]+)\\\".*", "$1");

		mockMvc.perform(post("/api/v1/jobs/{jobId}/cancel", jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void retriesJob_rejectsInvalidTransition() throws Exception {
		// A CREATED job cannot be manually retried; that is only valid for FAILED/CANCELLED/DLQ jobs.
		// The exception handler maps IllegalStateException to 409 CONFLICT.
		String request = "{\"jobType\":\"SIMULATED\",\"payload\":{},\"priority\":\"LOW\"}";
		String response = mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String jobId = response.replaceAll(".*\\\"jobId\\\":\\\"([^\\\"]+)\\\".*", "$1");

		mockMvc.perform(post("/api/v1/jobs/{jobId}/retry", jobId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_JOB_STATE"));
	}

	@Test
	void listsAndFiltersJobs() throws Exception {
		mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobType\":\"FILTER_ME\",\"payload\":{},\"priority\":\"MEDIUM\"}"));

		mockMvc.perform(get("/api/v1/jobs?jobType=FILTER_ME&priority=MEDIUM"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.totalElements").isNumber());
	}
}