"use client";

import { type RefObject, useCallback, useEffect, useRef, useState } from "react";

export interface PlaybackSync {
  /** Always-current playhead position in ms. Read this directly in a rAF draw loop (e.g. the
   *  canvas timeline) — it updates every frame without going through React state/re-renders. */
  currentTimeMsRef: RefObject<number>;
  /** Same value, but only re-rendered ~4x/sec — safe to use in JSX for a numeric time readout. */
  displayTimeMs: number;
  playing: boolean;
  durationMs: number;
  seekTo: (ms: number) => void;
  togglePlay: () => void;
}

const UI_UPDATE_INTERVAL_MS = 250;

/**
 * Bridges the native <video> element's playback state into React without paying a re-render per
 * 'timeupdate' event. Browsers fire 'timeupdate' at inconsistent, coarse rates (as low as ~4Hz on
 * some engines), which is both too slow for a smooth 60fps playhead and, if it *were* fast, would
 * be too many re-renders for a canvas-driven timeline anyway. Instead: a requestAnimationFrame
 * loop keeps a ref (`currentTimeMsRef`) current every frame for imperative consumers (the canvas
 * draw loop), and a separate, throttled `displayTimeMs` state value serves the one place that
 * actually needs to re-render — the on-screen time readout.
 */
export function usePlaybackSync(videoRef: RefObject<HTMLVideoElement | null>): PlaybackSync {
  const currentTimeMsRef = useRef(0);
  const lastUiUpdateRef = useRef(0);
  const [displayTimeMs, setDisplayTimeMs] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [durationMs, setDurationMs] = useState(0);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    let rafId: number;
    const tick = () => {
      currentTimeMsRef.current = video.currentTime * 1000;
      const now = performance.now();
      if (now - lastUiUpdateRef.current >= UI_UPDATE_INTERVAL_MS) {
        lastUiUpdateRef.current = now;
        setDisplayTimeMs(currentTimeMsRef.current);
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);

    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onLoadedMetadata = () => setDurationMs(video.duration * 1000 || 0);

    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    video.addEventListener("loadedmetadata", onLoadedMetadata);
    if (video.readyState >= 1) onLoadedMetadata();

    return () => {
      cancelAnimationFrame(rafId);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("loadedmetadata", onLoadedMetadata);
    };
  }, [videoRef]);

  const seekTo = useCallback(
    (ms: number) => {
      const video = videoRef.current;
      if (!video) return;
      video.currentTime = ms / 1000;
      currentTimeMsRef.current = ms;
      setDisplayTimeMs(ms);
    },
    [videoRef]
  );

  const togglePlay = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play();
    } else {
      video.pause();
    }
  }, [videoRef]);

  return { currentTimeMsRef, displayTimeMs, playing, durationMs, seekTo, togglePlay };
}
