"use client";

import { type RefObject, useState } from "react";
import { Pause, Play, Volume2, VolumeX } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { PlaybackSync } from "@/hooks/usePlaybackSync";
import { formatTimestamp } from "@/lib/utils";

interface VideoPlayerProps {
  videoRef: RefObject<HTMLVideoElement | null>;
  playback: PlaybackSync;
  src: string;
}

export function VideoPlayer({ videoRef, playback, src }: VideoPlayerProps) {
  const [muted, setMuted] = useState(false);

  return (
    <div className="flex flex-col gap-2">
      <div className="aspect-video w-full overflow-hidden rounded-lg bg-black">
        <video
          ref={videoRef}
          src={src}
          preload="metadata"
          muted={muted}
          className="h-full w-full"
          onClick={playback.togglePlay}
        />
      </div>

      <div className="flex items-center gap-3 rounded-md border border-border bg-card px-3 py-2">
        <Button variant="ghost" size="icon" onClick={playback.togglePlay}>
          {playback.playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
        </Button>
        <span className="min-w-[86px] font-mono text-xs text-muted-foreground">
          {formatTimestamp(playback.displayTimeMs)} / {formatTimestamp(playback.durationMs)}
        </span>
        <div className="flex-1" />
        <Button variant="ghost" size="icon" onClick={() => setMuted((m) => !m)}>
          {muted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
        </Button>
      </div>
    </div>
  );
}
