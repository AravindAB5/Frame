"use client";

import { FormEvent, useState } from "react";
import { Flag } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useCreateMarker } from "@/hooks/useMarkers";
import { formatTimestamp } from "@/lib/utils";

const COLOR_OPTIONS = ["#f87171", "#fbbf24", "#34d399", "#60a5fa", "#a78bfa", "#f472b6"];

interface MarkerCreateModalProps {
  videoId: string;
  currentTimeMsRef: React.RefObject<number>;
}

export function MarkerCreateModal({ videoId, currentTimeMsRef }: MarkerCreateModalProps) {
  const [open, setOpen] = useState(false);
  const [label, setLabel] = useState("");
  const [note, setNote] = useState("");
  const [color, setColor] = useState(COLOR_OPTIONS[0]);
  // Captured (not read live) when the dialog opens: reading a ref's .current during render is
  // disallowed (it can be stale/inconsistent under concurrent rendering) — capturing it in this
  // event handler, which runs outside of render, is the correct place to snapshot it.
  const [capturedTimeMs, setCapturedTimeMs] = useState(0);
  const createMarker = useCreateMarker();

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setLabel("");
      setNote("");
      setCapturedTimeMs(currentTimeMsRef.current ?? 0);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    createMarker.mutate(
      {
        videoId,
        timestampMs: Math.round(capturedTimeMs),
        label,
        note: note || undefined,
        color,
      },
      { onSuccess: () => setOpen(false) }
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Flag className="h-4 w-4" />
          Add marker
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add marker at {formatTimestamp(capturedTimeMs)}</DialogTitle>
        </DialogHeader>
        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="marker-label">Label</Label>
            <Input
              id="marker-label"
              required
              autoFocus
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="Needs review"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="marker-note">Note (optional)</Label>
            <Input id="marker-note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Color</Label>
            <div className="flex gap-2">
              {COLOR_OPTIONS.map((c) => (
                <button
                  key={c}
                  type="button"
                  aria-label={`Color ${c}`}
                  onClick={() => setColor(c)}
                  className="h-6 w-6 rounded-full"
                  style={{ backgroundColor: c, outline: color === c ? "2px solid white" : undefined, outlineOffset: 2 }}
                />
              ))}
            </div>
          </div>
          <DialogFooter>
            <Button type="submit" disabled={createMarker.isPending}>
              {createMarker.isPending ? "Adding…" : "Add marker"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
