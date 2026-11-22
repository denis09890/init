package dev.init.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawPostingRepository extends JpaRepository<RawPosting, Long> {

	boolean existsByCompanySourceIdAndExternalIdAndPayloadHash(Long companySourceId, String externalId,
			String payloadHash);

}
