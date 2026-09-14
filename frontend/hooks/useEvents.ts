import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { EventItem } from "@/lib/types";

/**
 * Fetches every event for a video once. Type filtering happens client-side (see the Timeline /
 * EventFilterBar components) rather than re-querying per toggle — at the data volumes here (tens
 * to low hundreds of events per video) a server round trip per filter click would only add
 * latency for no benefit.
 */
export function useEvents(videoId: string | undefined) {
  return useQuery({
    queryKey: ["videos", videoId, "events"],
    queryFn: () => apiClient.get<EventItem[]>(`/api/videos/${videoId}/events`),
    enabled: Boolean(videoId),
  });
}
