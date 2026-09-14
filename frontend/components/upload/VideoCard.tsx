"use client";

import Link from "next/link";
import { Trash2 } from "lucide-react";
import { useLatestJob } from "@/hooks/useVideos";
import { useProcessingProgress } from "@/hooks/useVideoWebSocket";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Card, CardContent } from "@/components/ui/card";
import type { Video, VideoStatus } from "@/lib/types";
import { formatTimestamp } from "@/lib/utils";

const STATUS_VARIANT: Record<VideoStatus, "default" | "secondary" | "success" | "warning" | "destructive"> = {
  UPLOADING: "secondary",
  QUEUED: "secondary",
  PROCESSING: "warning",
  READY: "success",
  FAILED: "destructive",
};

interface VideoCardProps {
  video: Video;
  onDelete: (id: string) => void;
  deleting: boolean;
}

export function VideoCard({ video, onDelete, deleting }: VideoCardProps) {
  const isActive = video.status === "QUEUED" || video.status === "PROCESSING";
  const progress = useProcessingProgress(isActive ? video.id : undefined);
  const { data: latestJob } = useLatestJob(isActive ? video.id : undefined);

  const percent = progress?.progressPercent ?? latestJob?.progressPercent ?? 0;
  const stage = progress?.stage ?? latestJob?.stage;

  return (
    <Card>
      <CardContent className="flex items-center gap-4 p-4">
        <Link href={`/videos/${video.id}`} className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="truncate font-medium">{video.title}</p>
            <Badge variant={STATUS_VARIANT[video.status]}>{video.status}</Badge>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {video.durationSeconds ? formatTimestamp(video.durationSeconds * 1000) : "—"} ·{" "}
            {(video.sizeBytes / (1024 * 1024)).toFixed(1)} MB
          </p>
          {isActive && (
            <div className="mt-2 flex items-center gap-2">
              <Progress value={percent} className="h-1.5 flex-1" />
              <span className="w-10 shrink-0 text-right text-xs text-muted-foreground">{percent}%</span>
            </div>
          )}
          {isActive && stage && <p className="mt-1 text-xs text-muted-foreground">{stage}</p>}
        </Link>
        <Button
          variant="ghost"
          size="icon"
          disabled={deleting}
          onClick={(e) => {
            e.preventDefault();
            onDelete(video.id);
          }}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </CardContent>
    </Card>
  );
}
