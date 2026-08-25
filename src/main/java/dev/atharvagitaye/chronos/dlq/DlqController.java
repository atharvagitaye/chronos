package dev.atharvagitaye.chronos.dlq;

import dev.atharvagitaye.chronos.job.dto.JobResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlq/jobs")
public class DlqController {

	private final DlqService dlqService;

	public DlqController(DlqService dlqService) {
		this.dlqService = dlqService;
	}

	@GetMapping
	public List<JobResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100),
				Sort.by(Sort.Direction.DESC, "updatedAt"));
		return dlqService.list(pageRequest).stream().map(JobResponse::from).toList();
	}

	@PostMapping("/{jobId}/replay")
	public JobResponse replay(@PathVariable UUID jobId) {
		return JobResponse.from(dlqService.replay(jobId));
	}
}