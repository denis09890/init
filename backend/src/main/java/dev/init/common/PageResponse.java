package dev.init.common;

import java.util.List;

/**
 * One page of a result list.
 *
 * @param page zero-based page number
 * @param size requested page size
 * @param totalItems number of items over all pages
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public PageResponse {
		items = List.copyOf(items);
	}

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
		int totalPages = size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
		return new PageResponse<>(items, page, size, totalItems, totalPages);
	}

}
