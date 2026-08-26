package dev.atharvagitaye.chronos.job.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static <T, U> PageResponse<T> from(Page<U> page, java.util.function.Function<U, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}
