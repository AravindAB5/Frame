// Mirrors backend/src/main/java/com/frame/dto/*.java — keep these in sync by hand (no shared
// codegen yet; documented as a follow-up trade-off in ARCHITECTURE.md).

export type VideoStatus = "UPLOADING" | "QUEUED" | "PROCESSING" | "READY" | "FAILED";

export type JobStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

export type EventType = "SCENE_CHANGE" | "CHAPTER" | "OCR_TEXT" | "AUDIO_EVENT" | "SPEAKER_CHANGE";

export const EVENT_TYPES: EventType[] = [
  "SCENE_CHANGE",
  "CHAPTER",
  "OCR_TEXT",
  "AUDIO_EVENT",
  "SPEAKER_CHANGE",
];

export interface User {
  id: string;
  email: string;
  displayName: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Video {
  id: string;
  title: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  durationSeconds: number | null;
  status: VideoStatus;
  hasThumbnail: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EventItem {
  id: string;
  videoId: string;
  eventType: EventType;
  timestampMs: number;
  endTimestampMs: number | null;
  title: string;
  description: string | null;
  metadata: Record<string, unknown>;
  confidence: number | null;
  createdAt: string;
}

export interface Marker {
  id: string;
  videoId: string;
  timestampMs: number;
  label: string;
  note: string | null;
  color: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessingJob {
  id: string;
  videoId: string;
  status: JobStatus;
  progressPercent: number;
  stage: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface ProcessingProgressMessage {
  videoId: string;
  jobId: string;
  status: JobStatus;
  progressPercent: number;
  stage: string | null;
  message: string | null;
}

export interface VideoStatusBroadcast {
  videoId: string;
  status: VideoStatus;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
