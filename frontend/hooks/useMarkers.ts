import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { Marker } from "@/lib/types";

export function useMarkers(videoId: string | undefined) {
  return useQuery({
    queryKey: ["videos", videoId, "markers"],
    queryFn: () => apiClient.get<Marker[]>(`/api/videos/${videoId}/markers`),
    enabled: Boolean(videoId),
  });
}

interface CreateMarkerInput {
  videoId: string;
  timestampMs: number;
  label: string;
  note?: string;
  color?: string;
}

export function useCreateMarker() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ videoId, ...body }: CreateMarkerInput) =>
      apiClient.post<Marker>(`/api/videos/${videoId}/markers`, body),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ["videos", variables.videoId, "markers"] });
    },
  });
}

export function useDeleteMarker(videoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (markerId: string) => apiClient.delete<void>(`/api/markers/${markerId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["videos", videoId, "markers"] });
    },
  });
}
