package dev.init.company;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read side of the companies API. */
@Repository
public class CompanyQueryRepository {

	private static final String ACTIVE_COMPANIES = """
			select c.name, c.slug, c.website,
			       count(p.id) filter (where p.status = 'OPEN') as open_postings,
			       count(p.id) filter (where p.status = 'OPEN' and p.is_early_career and p.location_eligible
			                           and p.duplicate_of_id is null) as early_career_postings
			from companies c
			left join postings p on p.company_id = c.id
			where c.active
			  and exists (select 1 from company_sources s where s.company_id = c.id and s.active)
			group by c.id
			order by c.name
			""";

	private final JdbcClient jdbc;

	public CompanyQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Active companies that have at least one active source, by name. */
	public List<CompanySummary> findActive() {
		return jdbc.sql(ACTIVE_COMPANIES)
			.query((rs, rowNumber) -> new CompanySummary(rs.getString("name"), rs.getString("slug"),
					rs.getString("website"), rs.getLong("open_postings"), rs.getLong("early_career_postings")))
			.list();
	}

}
