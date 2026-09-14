package com.frame.repository;

import com.frame.domain.entity.Video;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
