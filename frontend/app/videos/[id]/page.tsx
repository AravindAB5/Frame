"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { RequireAuth } from "@/components/providers/RequireAuth";
import { VideoPlayer } from "@/components/player/VideoPlayer";
import { Timeline } from "@/components/timeline/Timeline";
import { EventFilterBar } from "@/components/events/EventFilterBar";
import { EventInspectorPanel } from "@/components/events/EventInspectorPanel";
import { MarkerCreateModal } from "@/components/markers/MarkerCreateModal";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { usePlaybackSync } from "@/hooks/usePlaybackSync";
import { useLatestJob, useVideo } from "@/hooks/useVideos";
import { useEvents } from "@/hooks/useEvents";
import { useMarkers } from "@/hooks/useMarkers";
import { useProcessingProgress } from "@/hooks/useVideoWebSocket";
import { streamUrl } from "@/lib/api-client";
import { EVENT_TYPES, type EventItem, type EventType } from "@/lib/types";

function ReviewWorkspace({ videoId }: { videoId: string }) {
  const { data: video } = useVideo(videoId);
  const { data: events } = useEvents(videoId);
  const { data: markers } = useMarkers(videoId);

  const videoRef = useRef<HTMLVideoElement>(null);
  const playback = usePlaybackSync(videoRef);

  const [activeTypes, setActiveTypes] = useState<Set<EventType>>(new Set(EVENT_TYPES));
  const [selectedEvent, setSelectedEvent] = useState<EventItem | null>(null);

  const toggleType = (type: EventType) =>
    setActiveTypes((prev) => {
      const next = new Set(prev);
      if (next.has(type)) {
        next.delete(type);
      } else {
        next.add(type);
      }
      return next;
    });

  const durationMs = useMemo(
    () => (video?.durationSeconds ? video.durationSeconds * 1000 : playback.durationMs),
    [video?.durationSeconds, playback.durationMs]
  );

  if (!video) {
    return <p className="p-8 text-sm text-muted-foreground">Loading…</p>;
  }

  if (video.status !== "READY") {
    return <ProcessingState videoId={videoId} title={video.title} status={video.status} />;
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-6">
      <header className="flex items-center gap-3">
        <Link href="/" className="text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <h1 className="truncate text-lg font-semibold">{video.title}</h1>
      </header>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_300px]">
        <VideoPlayer videoRef={videoRef} playback={playback} src={streamUrl(videoId)} />
        <EventInspectorPanel event={selectedEvent} />
      </div>

      <div className="flex items-center justify-between gap-3">
        <EventFilterBar events={events ?? []} activeTypes={activeTypes} onToggle={toggleType} />
        <MarkerCreateModal videoId={videoId} currentTimeMsRef={playback.currentTimeMsRef} />
      </div>

      <Timeline
        durationMs={durationMs}
        events={events ?? []}
        markers={markers ?? []}
        activeTypes={activeTypes}
        currentTimeMsRef={playback.currentTimeMsRef}
        selectedEventId={selectedEvent?.id ?? null}
        onSeek={playback.seekTo}
        onSelectEvent={setSelectedEvent}
      />
    </div>
  );
}

function ProcessingState({
  videoId,
  title,
  status,
}: {
  videoId: string;
  title: string;
  status: string;
}) {
  const progress = useProcessingProgress(videoId);
  const { data: latestJob } = useLatestJob(videoId);
  const percent = progress?.progressPercent ?? latestJob?.progressPercent ?? 0;
  const stage = progress?.stage ?? latestJob?.stage;
  const failed = status === "FAILED";

  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-24 text-center">
      <Link href="/" className="self-start text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" />
      </Link>
      <h1 className="text-lg font-semibold">{title}</h1>
      <Badge variant={failed ? "destructive" : "warning"}>{status}</Badge>
      {!failed ? (
        <>
          <Progress value={percent} className="w-full" />
          <p className="text-sm text-muted-foreground">{stage ?? "Waiting to start…"}</p>
        </>
      ) : (
        <p className="text-sm text-muted-foreground">
          {latestJob?.errorMessage ?? "Processing failed. Try uploading the video again."}
        </p>
      )}
    </div>
  );
}

export default function VideoPage() {
  const params = useParams<{ id: string }>();

  return (
    <RequireAuth>
      <ReviewWorkspace videoId={params.id} />
    </RequireAuth>
  );
}
