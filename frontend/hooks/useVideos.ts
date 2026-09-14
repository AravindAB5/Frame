import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { ProcessingJob, Video } from "@/lib/types";

export function useVideos() {
  return useQuery({
    queryKey: ["videos"],
    queryFn: () => apiClient.get<Video[]>("/api/videos"),
  });
}

export function useVideo(videoId: string | undefined) {
  return useQuery({
    queryKey: ["videos", videoId],
    queryFn: () => apiClient.get<Video>(`/api/videos/${videoId}`),
    enabled: Boolean(videoId),
  });
}

export function useLatestJob(videoId: string | undefined) {
  return useQuery({
    queryKey: ["videos", videoId, "jobs", "latest"],
    queryFn: () => apiClient.get<ProcessingJob>(`/api/videos/${videoId}/jobs/latest`),
    enabled: Boolean(videoId),
  });
}

interface UploadVideoInput {
  file: File;
  title: string;
  durationSeconds: number;
}

export function useUploadVideo() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file, title, durationSeconds }: UploadVideoInput) => {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("title", title);
      formData.append("durationSeconds", String(durationSeconds));
      return apiClient.upload<Video>("/api/videos", formData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["videos"] });
    },
  });
}

export function useDeleteVideo() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (videoId: string) => apiClient.delete<void>(`/api/videos/${videoId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["videos"] });
    },
  });
}
