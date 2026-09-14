"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient, onStompConnect } from "@/lib/ws-client";
import type { ProcessingProgressMessage, Video, VideoStatusBroadcast } from "@/lib/types";

/** Subscribes to the dashboard-wide status broadcast and keeps the video list query fresh. */
export function useVideoStatusBroadcast() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const client = getStompClient();
    let subscription: ReturnType<typeof client.subscribe> | undefined;

    const unregister = onStompConnect(() => {
      subscription = client.subscribe("/topic/videos", (message) => {
        const payload = JSON.parse(message.body) as VideoStatusBroadcast;
        queryClient.setQueryData<Video[]>(["videos"], (current) =>
          current?.map((v) => (v.id === payload.videoId ? { ...v, status: payload.status } : v))
        );
      });
    });

    return () => {
      unregister();
      subscription?.unsubscribe();
    };
  }, [queryClient]);
}

/** Subscribes to one video's processing progress and keeps dependent queries fresh as it completes. */
export function useProcessingProgress(videoId: string | undefined) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<ProcessingProgressMessage | null>(null);

  useEffect(() => {
    if (!videoId) return;

    const client = getStompClient();
    let subscription: ReturnType<typeof client.subscribe> | undefined;

    const unregister = onStompConnect(() => {
      subscription = client.subscribe(`/topic/videos/${videoId}/progress`, (message) => {
        const payload = JSON.parse(message.body) as ProcessingProgressMessage;
        setProgress(payload);

        queryClient.invalidateQueries({ queryKey: ["videos", videoId, "jobs", "latest"] });
        queryClient.invalidateQueries({ queryKey: ["videos", videoId] });

        if (payload.status === "COMPLETED" || payload.status === "FAILED") {
          queryClient.invalidateQueries({ queryKey: ["videos", videoId, "events"] });
        }
      });
    });

    return () => {
      unregister();
      subscription?.unsubscribe();
    };
  }, [videoId, queryClient]);

  return progress;
}
