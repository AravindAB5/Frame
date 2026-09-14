"use client";

import { EVENT_TYPES, type EventItem, type EventType } from "@/lib/types";
import { cn } from "@/lib/utils";

const LABELS: Record<EventType, string> = {
  SCENE_CHANGE: "Scenes",
  CHAPTER: "Chapters",
  OCR_TEXT: "OCR Text",
  AUDIO_EVENT: "Audio",
  SPEAKER_CHANGE: "Speakers",
};

const COLORS: Record<EventType, string> = {
  SCENE_CHANGE: "#60a5fa",
  CHAPTER: "#a78bfa",
  OCR_TEXT: "#34d399",
  AUDIO_EVENT: "#fbbf24",
  SPEAKER_CHANGE: "#f472b6",
};

interface EventFilterBarProps {
  events: EventItem[];
  activeTypes: Set<EventType>;
  onToggle: (type: EventType) => void;
}

export function EventFilterBar({ events, activeTypes, onToggle }: EventFilterBarProps) {
  const counts = events.reduce<Record<string, number>>((acc, e) => {
    acc[e.eventType] = (acc[e.eventType] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <div className="flex flex-wrap gap-2">
      {EVENT_TYPES.map((type) => {
        const active = activeTypes.has(type);
        return (
          <button
            key={type}
            type="button"
            onClick={() => onToggle(type)}
            className={cn(
              "flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
              active ? "border-transparent text-white" : "border-border text-muted-foreground hover:text-foreground"
            )}
            style={active ? { backgroundColor: COLORS[type] } : undefined}
          >
            <span
              className="h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: active ? "white" : COLORS[type] }}
            />
            {LABELS[type]}
            <span className="opacity-70">{counts[type] ?? 0}</span>
          </button>
        );
      })}
    </div>
  );
}
