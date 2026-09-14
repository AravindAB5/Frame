"use client";

import { useCallback, useRef, useState } from "react";
import { UploadCloud } from "lucide-react";
import { useUploadVideo } from "@/hooks/useVideos";
import { cn } from "@/lib/utils";

/** Reads video duration client-side (via a throwaway <video> element) before upload, so the
 *  backend never has to guess it and the mock processor can scale event timestamps to something
 *  that actually matches the file — no ffprobe/server-side parsing needed for a value the browser
 *  already knows for free. */
function readDurationSeconds(file: File): Promise<number> {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    video.preload = "metadata";
    video.onloadedmetadata = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : 0;
      URL.revokeObjectURL(video.src);
      resolve(duration);
    };
    video.onerror = () => resolve(0);
    video.src = URL.createObjectURL(file);
  });
}

export function UploadDropzone() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const upload = useUploadVideo();

  const handleFile = useCallback(
    async (file: File) => {
      setError(null);
      if (!file.type.startsWith("video/")) {
        setError("Please choose a video file.");
        return;
      }
      const durationSeconds = await readDurationSeconds(file);
      const title = file.name.replace(/\.[^/.]+$/, "");
      upload.mutate(
        { file, title, durationSeconds },
        { onError: () => setError("Upload failed. Please try again.") }
      );
    },
    [upload]
  );

  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border p-10 text-center transition-colors",
        dragging && "border-primary bg-primary/5",
        upload.isPending && "pointer-events-none opacity-60"
      )}
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragging(false);
        const file = e.dataTransfer.files?.[0];
        if (file) void handleFile(file);
      }}
    >
      <UploadCloud className="h-8 w-8 text-muted-foreground" />
      <p className="text-sm text-muted-foreground">
        {upload.isPending ? "Uploading…" : "Drag a video here, or"}
      </p>
      {!upload.isPending && (
        <button
          type="button"
          className="text-sm font-medium text-primary hover:underline"
          onClick={() => inputRef.current?.click()}
        >
          browse files
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="video/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
          e.target.value = "";
        }}
      />
      {error && <p className="text-sm text-red-400">{error}</p>}
    </div>
  );
}
