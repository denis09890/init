package dev.init.posting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.common.Diacritics;
import dev.init.common.PageResponse;
import dev.init.posting.PostingResponse.CompanyRef;
import dev.init.source.RemoteType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read side of the postings API. Plain SQL instead of JPA because the queries use PostgreSQL
 * features (full-text search, array functions) and return flat response records. Every value
 * reaches the database as a bound parameter; the only text added to the SQL are fixed fragments.
 */
@Repository
public class PostingSearchRepository {

	private static final String SELECT = """
			select p.id, p.title, c.name as company_name, c.slug as company_slug, p.url, p.locations,
			       p.remote_type, p.snippet, p.posted_at, p.first_seen_at, p.last_seen_at, p.closed_at, p.status,
			       p.is_early_career, p.location_eligible, p.opportunity_type, p.domain, p.seniority, p.language,
			       p.paid, p.skills
			from postings p
			join companies c on c.id = p.company_id
			""";

	private static final String COUNT = """
			select count(*)
			from postings p
			join companies c on c.id = p.company_id
			""";

	private final JdbcClient jdbc;

	public PostingSearchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Open postings matching the request, newest first (best match first when searching text). */
	public PageResponse<PostingResponse> search(PostingSearchRequest request) {
		Query query = Query.of(request);
		int pageNumber = request.pageNumber();
		int pageSize = request.pageSize();

		long total = jdbc.sql(COUNT + "where " + query.where())
			.params(query.params())
			.query(Long.class)
			.single();
		List<PostingResponse> items = jdbc
			.sql(SELECT + "where " + query.where() + " order by " + query.orderBy() + " limit :limit offset :offset")
			.params(query.params())
			.param("limit", pageSize)
			.param("offset", (long) pageNumber * pageSize)
			.query(PostingSearchRepository::map)
			.list();
		return PageResponse.of(items, pageNumber, pageSize, total);
	}

	/** Any posting by id, open or closed. */
	public Optional<PostingResponse> findById(long id) {
		return jdbc.sql(SELECT + "where p.id = :id").param("id", id).query(PostingSearchRepository::map).optional();
	}

	private record Query(String where, String orderBy, Map<String, Object> params) {

		static Query of(PostingSearchRequest request) {
			List<String> conditions = new ArrayList<>();
			Map<String, Object> params = new HashMap<>();
			conditions.add("p.status = 'OPEN'");
			conditions.add("p.duplicate_of_id is null");
			if (request.earlyCareerOnly()) {
				conditions.add("p.is_early_career");
			}
			if (request.romaniaOnlyFilter()) {
				conditions.add("p.location_eligible");
			}

			String text = request.searchText();
			if (text != null) {
				conditions.add("p.search_vector @@ plainto_tsquery('simple', :q)");
				params.put("q", text);
			}
			if (!request.domains().isEmpty()) {
				conditions.add("p.domain in (:domains)");
				params.put("domains", request.domains().stream().map(Domain::name).toList());
			}
			if (!request.types().isEmpty()) {
				conditions.add("p.opportunity_type in (:types)");
				params.put("types", request.types().stream().map(OpportunityType::name).toList());
			}
			if (request.companySlug() != null) {
				conditions.add("c.slug = :company");
				params.put("company", request.companySlug());
			}
			String city = request.cityText();
			if (city != null) {
				conditions.add("exists (select 1 from unnest(p.locations) as loc(place) where lower(translate("
						+ "loc.place, '" + Diacritics.FROM + "', '" + Diacritics.TO + "')) like :city)");
				params.put("city", "%" + escapeLike(city) + "%");
			}
			if (request.remote() != null) {
				conditions.add("p.remote_type = :remote");
				params.put("remote", request.remote().name());
			}
			if (request.languageCode() != null) {
				conditions.add("p.language = :language");
				params.put("language", request.languageCode());
			}
			if (request.paid() != null) {
				conditions.add("p.paid = :paid");
				params.put("paid", request.paid());
			}

			String newestFirst = "p.posted_at desc nulls last, p.id desc";
			String orderBy = text == null ? newestFirst
					: "ts_rank(p.search_vector, plainto_tsquery('simple', :q)) desc, " + newestFirst;
			return new Query(String.join(" and ", conditions), orderBy, params);
		}

		/** The backslash is PostgreSQL's default escape character for LIKE. */
		private static String escapeLike(String text) {
			return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		}

	}

	private static PostingResponse map(ResultSet rs, int rowNumber) throws SQLException {
		return new PostingResponse(
				rs.getLong("id"),
				rs.getString("title"),
				new CompanyRef(rs.getString("company_name"), rs.getString("company_slug")),
				rs.getString("url"),
				List.of((String[]) rs.getArray("locations").getArray()),
				RemoteType.valueOf(rs.getString("remote_type")),
				rs.getString("snippet"),
				instant(rs, "posted_at"),
				instant(rs, "first_seen_at"),
				instant(rs, "last_seen_at"),
				instant(rs, "closed_at"),
				PostingStatus.valueOf(rs.getString("status")),
				rs.getBoolean("is_early_career"),
				rs.getBoolean("location_eligible"),
				OpportunityType.valueOf(rs.getString("opportunity_type")),
				Domain.valueOf(rs.getString("domain")),
				Seniority.valueOf(rs.getString("seniority")),
				rs.getString("language"),
				rs.getObject("paid", Boolean.class),
				List.of((String[]) rs.getArray("skills").getArray()));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
