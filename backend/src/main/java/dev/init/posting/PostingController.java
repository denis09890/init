package dev.init.posting;

import dev.init.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/postings")
class PostingController {

	private final PostingSearchRepository postings;

	PostingController(PostingSearchRepository postings) {
		this.postings = postings;
	}

	/** Open early-career postings in Romania by default; see {@link PostingSearchRequest} for the filters. */
	@GetMapping
	PageResponse<PostingResponse> search(@Valid PostingSearchRequest request) {
		return postings.search(request);
	}

	@GetMapping("/{id}")
	PostingResponse get(@PathVariable long id) {
		return postings.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No posting with id " + id));
	}

}
