package dev.init.posting;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRepository extends JpaRepository<Posting, Long> {

	Optional<Posting> findByCompanySourceIdAndExternalId(Long companySourceId, String externalId);

}
