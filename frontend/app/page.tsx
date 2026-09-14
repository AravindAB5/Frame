"use client";

import { RequireAuth } from "@/components/providers/RequireAuth";
import { UploadDropzone } from "@/components/upload/UploadDropzone";
import { VideoCard } from "@/components/upload/VideoCard";
import { Button } from "@/components/ui/button";
import { useDeleteVideo, useVideos } from "@/hooks/useVideos";
import { useVideoStatusBroadcast } from "@/hooks/useVideoWebSocket";
import { useAuthStore } from "@/lib/auth";

function Dashboard() {
  const { data: videos, isLoading } = useVideos();
  const deleteVideo = useDeleteVideo();
  const { user, clearAuth } = useAuthStore();
  useVideoStatusBroadcast();

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <header className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Frame</h1>
          <p className="text-sm text-muted-foreground">Video analysis and review workspace</p>
        </div>
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <span>{user?.displayName}</span>
          <Button variant="outline" size="sm" onClick={clearAuth}>
            Sign out
          </Button>
        </div>
      </header>

      <UploadDropzone />

      <div className="mt-8 flex flex-col gap-3">
        {isLoading && <p className="text-sm text-muted-foreground">Loading videos…</p>}
        {!isLoading && videos?.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            No videos yet — upload one to get started.
          </p>
        )}
        {videos?.map((video) => (
          <VideoCard
            key={video.id}
            video={video}
            deleting={deleteVideo.isPending && deleteVideo.variables === video.id}
            onDelete={(id) => deleteVideo.mutate(id)}
          />
        ))}
      </div>
    </div>
  );
}

export default function HomePage() {
  return (
    <RequireAuth>
      <Dashboard />
    </RequireAuth>
  );
}
