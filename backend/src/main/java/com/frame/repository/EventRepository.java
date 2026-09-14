package com.frame.repository;

import com.frame.domain.entity.Event;
import com.frame.domain.entity.EventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByVideoIdOrderByTimestampMsAsc(UUID videoId);

    void deleteByVideoId(UUID videoId);

    /**
     * Split into two queries (rather than one with "{@code :eventTypes is null or e.eventType in
     * :eventTypes}") because binding a null collection to an {@code IN} parameter is inconsistent
     * across Hibernate versions/dialects — safer to never do it.
     */
    @Query("""
        select e from Event e
        where e.videoId = :videoId
          and (:fromMs is null or e.timestampMs >= :fromMs)
          and (:toMs is null or e.timestampMs <= :toMs)
        order by e.timestampMs asc
        """)
    List<Event> searchAllTypes(
            @Param("videoId") UUID videoId, @Param("fromMs") Long fromMs, @Param("toMs") Long toMs);

    @Query("""
        select e from Event e
        where e.videoId = :videoId
          and e.eventType in :eventTypes
          and (:fromMs is null or e.timestampMs >= :fromMs)
          and (:toMs is null or e.timestampMs <= :toMs)
        order by e.timestampMs asc
        """)
    List<Event> searchByTypes(
            @Param("videoId") UUID videoId,
            @Param("eventTypes") List<EventType> eventTypes,
            @Param("fromMs") Long fromMs,
            @Param("toMs") Long toMs);
}
