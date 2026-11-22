package dev.init.company;

import java.util.List;
import java.util.Optional;

import dev.init.source.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySourceRepository extends JpaRepository<CompanySource, Long> {

	Optional<CompanySource> findBySourceTypeAndIdentifier(SourceType sourceType, String identifier);

	List<CompanySource> findByActiveTrue();

}
