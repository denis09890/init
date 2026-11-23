package dev.init.company;

import java.util.List;
import java.util.Optional;

import dev.init.source.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanySourceRepository extends JpaRepository<CompanySource, Long> {

	Optional<CompanySource> findBySourceTypeAndIdentifier(SourceType sourceType, String identifier);

	List<CompanySource> findByActiveTrue();

	/** Active sources of active companies, with the company loaded so it can be read outside a transaction. */
	@Query("select s from CompanySource s join fetch s.company c where s.active = true and c.active = true")
	List<CompanySource> findActiveWithCompany();

}
