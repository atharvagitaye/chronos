package dev.atharvagitaye.chronos.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}