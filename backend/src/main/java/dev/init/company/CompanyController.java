package dev.init.company;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
class CompanyController {

	private final CompanyQueryRepository companies;

	CompanyController(CompanyQueryRepository companies) {
		this.companies = companies;
	}

	@GetMapping
	List<CompanySummary> list() {
		return companies.findActive();
	}

}
