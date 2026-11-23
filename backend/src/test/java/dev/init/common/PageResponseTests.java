package dev.init.common;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTests {

	@ParameterizedTest(name = "{0} items, page size {1} -> {2} pages")
	@CsvSource({ "0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "100, 20, 5", "101, 20, 6", "5, 0, 0" })
	void countsThePages(long totalItems, int size, int expectedPages) {
		assertThat(PageResponse.of(List.of(), 0, size, totalItems).totalPages()).isEqualTo(expectedPages);
	}

	@Test
	void keepsTheValuesItWasGiven() {
		PageResponse<String> page = PageResponse.of(List.of("a", "b"), 2, 2, 5);

		assertThat(page.items()).containsExactly("a", "b");
		assertThat(page.page()).isEqualTo(2);
		assertThat(page.size()).isEqualTo(2);
		assertThat(page.totalItems()).isEqualTo(5);
	}

	@Test
	void itemsCannotBeChangedAfterwards() {
		List<String> items = new ArrayList<>(List.of("a"));
		PageResponse<String> page = PageResponse.of(items, 0, 10, 1);
		items.add("b");

		assertThat(page.items()).containsExactly("a");
		assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
	}

}
