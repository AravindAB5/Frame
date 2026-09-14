import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiBaseUrl } from "./api-client";
import { useAuthStore } from "./auth";

/**
 * One shared STOMP-over-SockJS connection for the whole app. Multiple hooks subscribe to
 * different topics on this same client; `onStompConnect` below is a small pub-sub registry so
 * each hook can register/unregister its own "(re)subscribe on connect" callback independently,
 * rather than every hook fighting over the single `client.onConnect` property (which would
 * silently clobber whichever hook registered first).
 */
let sharedClient: Client | null = null;
const connectListeners = new Set<() => void>();

export function getStompClient(): Client {
  if (sharedClient) {
    return sharedClient;
  }

  const token = useAuthStore.getState().token ?? "";
  const client = new Client({
    webSocketFactory: () => new SockJS(`${apiBaseUrl()}/ws?token=${encodeURIComponent(token)}`),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
  client.onConnect = () => connectListeners.forEach((listener) => listener());
  client.activate();

  sharedClient = client;
  return client;
}

/** Registers a callback to run once connected, and again on every reconnect. Returns an unregister fn. */
export function onStompConnect(listener: () => void): () => void {
  const client = getStompClient();
  connectListeners.add(listener);
  if (client.connected) {
    listener();
  }
  return () => connectListeners.delete(listener);
}

export function resetStompClient() {
  sharedClient?.deactivate();
  sharedClient = null;
  connectListeners.clear();
}
