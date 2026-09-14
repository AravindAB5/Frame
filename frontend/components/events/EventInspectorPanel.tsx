"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { EventItem } from "@/lib/types";
import { formatTimestamp } from "@/lib/utils";

interface EventInspectorPanelProps {
  event: EventItem | null;
}

export function EventInspectorPanel({ event }: EventInspectorPanelProps) {
  if (!event) {
    return (
      <Card>
        <CardContent className="p-4 text-sm text-muted-foreground">
          Select an event on the timeline to see its details.
        </CardContent>
      </Card>
    );
  }

  const metadataEntries = Object.entries(event.metadata ?? {});

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>{event.title}</CardTitle>
          <Badge variant="outline">{event.eventType.replace("_", " ")}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 pt-0 text-sm">
        <div className="flex items-center gap-2 text-muted-foreground">
          <span>{formatTimestamp(event.timestampMs)}</span>
          {event.endTimestampMs != null && <span>→ {formatTimestamp(event.endTimestampMs)}</span>}
        </div>

        {event.description && <p>{event.description}</p>}

        {event.confidence != null && (
          <p className="text-xs text-muted-foreground">
            Confidence: {(event.confidence * 100).toFixed(0)}%
          </p>
        )}

        {metadataEntries.length > 0 && (
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 border-t border-border pt-3 text-xs">
            {metadataEntries.map(([key, value]) => (
              <FormattedMetadataEntry key={key} k={key} v={value} />
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  );
}

function FormattedMetadataEntry({ k, v }: { k: string; v: unknown }) {
  return (
    <>
      <dt className="text-muted-foreground">{k}</dt>
      <dd className="truncate">{typeof v === "object" ? JSON.stringify(v) : String(v)}</dd>
    </>
  );
}
