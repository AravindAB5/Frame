"use client";

import { type RefObject, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { EventItem, EventType, Marker } from "@/lib/types";
import { formatTimestamp } from "@/lib/utils";

interface TrackConfig {
  type: EventType;
  label: string;
  color: string;
  isRange: boolean;
}

const TRACKS: TrackConfig[] = [
  { type: "CHAPTER", label: "Chapters", color: "#a78bfa", isRange: true },
  { type: "SPEAKER_CHANGE", label: "Speakers", color: "#f472b6", isRange: true },
  { type: "SCENE_CHANGE", label: "Scenes", color: "#60a5fa", isRange: false },
  { type: "AUDIO_EVENT", label: "Audio", color: "#fbbf24", isRange: false },
  { type: "OCR_TEXT", label: "OCR Text", color: "#34d399", isRange: false },
];

const RULER_HEIGHT = 28;
const TRACK_HEIGHT = 32;
const MARKER_DOT_SIZE = 10;

interface TimelineProps {
  durationMs: number;
  events: EventItem[];
  markers: Marker[];
  activeTypes: Set<EventType>;
  currentTimeMsRef: RefObject<number>;
  selectedEventId: string | null;
  onSeek: (ms: number) => void;
  onSelectEvent: (event: EventItem) => void;
}

/**
 * Hybrid rendering: a <canvas> draws the ruler background and the playhead line every animation
 * frame (cheap to redraw at 60fps, and reads currentTimeMsRef directly — no React re-render per
 * frame). Individual event markers are absolutely-positioned DOM elements layered on top, so
 * click/hover/tooltip/keyboard-focus behavior is just ordinary React/DOM instead of hand-rolled
 * canvas hit-testing. Chosen over an all-canvas timeline because the event count here (tens to
 * low hundreds) is nowhere near where DOM node overhead would matter, and over an all-DOM
 * timeline because redrawing a playhead line via React state on every frame would re-render far
 * more than necessary.
 */
export function Timeline({
  durationMs,
  events,
  markers,
  activeTypes,
  currentTimeMsRef,
  selectedEventId,
  onSeek,
  onSelectEvent,
}: TimelineProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [width, setWidth] = useState(0);

  const visibleTracks = TRACKS.filter((t) => activeTypes.has(t.type));
  const totalHeight = RULER_HEIGHT + visibleTracks.length * TRACK_HEIGHT + TRACK_HEIGHT; // + markers track

  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || width === 0) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = width * dpr;
    canvas.height = totalHeight * dpr;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${totalHeight}px`;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.scale(dpr, dpr);

    let rafId: number;
    const draw = () => {
      ctx.clearRect(0, 0, width, totalHeight);
      drawRuler(ctx, width, durationMs);
      drawPlayhead(ctx, width, totalHeight, durationMs, currentTimeMsRef.current ?? 0);
      rafId = requestAnimationFrame(draw);
    };
    rafId = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(rafId);
  }, [width, totalHeight, durationMs, currentTimeMsRef]);

  function xToMs(clientX: number): number {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect || durationMs === 0) return 0;
    const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    return ratio * durationMs;
  }

  return (
    <div
      ref={containerRef}
      className="relative w-full select-none rounded-md border border-border bg-card"
      style={{ height: totalHeight }}
      onMouseDown={(e) => onSeek(xToMs(e.clientX))}
    >
      <canvas ref={canvasRef} className="absolute inset-0 pointer-events-none" />

      {/* Ruler labels (DOM, since text-in-canvas is a pain to keep crisp at arbitrary DPR) */}
      <div className="pointer-events-none absolute inset-x-0 top-0" style={{ height: RULER_HEIGHT }}>
        <RulerLabels durationMs={durationMs} width={width} />
      </div>

      {visibleTracks.map((track, i) => (
        <TrackRow
          key={track.type}
          top={RULER_HEIGHT + i * TRACK_HEIGHT}
          label={track.label}
          color={track.color}
        >
          {events
            .filter((e) => e.eventType === track.type)
            .map((event) => (
              <EventMarker
                key={event.id}
                event={event}
                track={track}
                durationMs={durationMs}
                selected={event.id === selectedEventId}
                onClick={() => {
                  onSelectEvent(event);
                  onSeek(event.timestampMs);
                }}
              />
            ))}
        </TrackRow>
      ))}

      <TrackRow top={RULER_HEIGHT + visibleTracks.length * TRACK_HEIGHT} label="Markers" color="#f87171">
        {markers.map((marker) => (
          <div
            key={marker.id}
            title={marker.label}
            className="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 cursor-pointer rounded-full border-2 border-background shadow"
            style={{
              left: `${(marker.timestampMs / durationMs) * 100}%`,
              width: MARKER_DOT_SIZE,
              height: MARKER_DOT_SIZE,
              backgroundColor: marker.color ?? "#f87171",
            }}
            onClick={(e) => {
              e.stopPropagation();
              onSeek(marker.timestampMs);
            }}
          />
        ))}
      </TrackRow>
    </div>
  );
}

function TrackRow({
  top,
  label,
  color,
  children,
}: {
  top: number;
  label: string;
  color: string;
  children: React.ReactNode;
}) {
  return (
    <div className="absolute inset-x-0 border-t border-border/60" style={{ top, height: TRACK_HEIGHT }}>
      <span
        className="pointer-events-none absolute left-1.5 top-1/2 -translate-y-1/2 text-[10px] font-medium uppercase tracking-wide opacity-70"
        style={{ color }}
      >
        {label}
      </span>
      <div className="relative h-full">{children}</div>
    </div>
  );
}

function EventMarker({
  event,
  track,
  durationMs,
  selected,
  onClick,
}: {
  event: EventItem;
  track: TrackConfig;
  durationMs: number;
  selected: boolean;
  onClick: () => void;
}) {
  const startPct = (event.timestampMs / durationMs) * 100;

  if (track.isRange && event.endTimestampMs != null) {
    const widthPct = ((event.endTimestampMs - event.timestampMs) / durationMs) * 100;
    return (
      <div
        title={event.title}
        className="absolute top-1/2 h-4 -translate-y-1/2 cursor-pointer overflow-hidden rounded-sm border text-[10px] leading-4 text-white/90"
        style={{
          left: `${startPct}%`,
          width: `${widthPct}%`,
          backgroundColor: `${track.color}33`,
          borderColor: selected ? "#ffffff" : `${track.color}80`,
        }}
        onClick={(e) => {
          e.stopPropagation();
          onClick();
        }}
      >
        <span className="ml-1 truncate">{event.title}</span>
      </div>
    );
  }

  return (
    <button
      type="button"
      title={event.title}
      className="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full transition-transform hover:scale-125"
      style={{
        left: `${startPct}%`,
        width: selected ? 12 : 9,
        height: selected ? 12 : 9,
        backgroundColor: track.color,
        boxShadow: selected ? `0 0 0 2px white` : undefined,
      }}
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
    />
  );
}

function RulerLabels({ durationMs, width }: { durationMs: number; width: number }) {
  if (durationMs === 0 || width === 0) return null;
  const step = niceStepMs(durationMs, width);
  const labels: React.ReactNode[] = [];
  for (let t = 0; t <= durationMs; t += step) {
    labels.push(
      <span
        key={t}
        className="absolute top-1.5 text-[10px] text-muted-foreground"
        style={{ left: `${(t / durationMs) * 100}%` }}
      >
        {formatTimestamp(t)}
      </span>
    );
  }
  return <>{labels}</>;
}

function niceStepMs(durationMs: number, width: number): number {
  const targetTicks = Math.max(2, Math.floor(width / 90));
  const rawStep = durationMs / targetTicks;
  const steps = [1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000];
  return steps.find((s) => s >= rawStep) ?? steps[steps.length - 1];
}

function drawRuler(ctx: CanvasRenderingContext2D, width: number, durationMs: number) {
  ctx.strokeStyle = "rgba(148, 163, 184, 0.25)";
  ctx.lineWidth = 1;
  if (durationMs === 0) return;
  const step = niceStepMs(durationMs, width);
  for (let t = 0; t <= durationMs; t += step) {
    const x = Math.round((t / durationMs) * width) + 0.5;
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, RULER_HEIGHT);
    ctx.stroke();
  }
}

function drawPlayhead(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  durationMs: number,
  currentTimeMs: number
) {
  if (durationMs === 0) return;
  const x = Math.round((Math.min(currentTimeMs, durationMs) / durationMs) * width) + 0.5;
  ctx.strokeStyle = "#f8fafc";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(x, 0);
  ctx.lineTo(x, height);
  ctx.stroke();

  ctx.fillStyle = "#f8fafc";
  ctx.beginPath();
  ctx.moveTo(x - 4, 0);
  ctx.lineTo(x + 4, 0);
  ctx.lineTo(x, 6);
  ctx.closePath();
  ctx.fill();
}
