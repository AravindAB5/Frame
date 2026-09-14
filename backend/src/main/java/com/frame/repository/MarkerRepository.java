package com.frame.repository;

import com.frame.domain.entity.Marker;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkerRepository extends JpaRepository<Marker, UUID> {

    List<Marker> findByVideoIdOrderByTimestampMsAsc(UUID videoId);
}
