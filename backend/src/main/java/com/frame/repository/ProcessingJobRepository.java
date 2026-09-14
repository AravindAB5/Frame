package com.frame.repository;

import com.frame.domain.entity.ProcessingJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    List<ProcessingJob> findByVideoIdOrderByCreatedAtDesc(UUID videoId);

    Optional<ProcessingJob> findFirstByVideoIdOrderByCreatedAtDesc(UUID videoId);
}
