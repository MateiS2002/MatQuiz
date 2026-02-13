/**
 * Shared STOMP client with native WebSocket first and lazy SockJS fallback for
 * legacy browsers, plus connection reuse helpers.
 */

import {
  Client,
  type IFrame,
  type IMessage,
  type StompSubscription,
} from "@stomp/stompjs"

type MessageParser<T> = (body: string) => T
type Unsubscribe = () => void

const DEFAULT_WS_URL = "http://localhost:8080/ws"

let client: Client | null = null
let activeToken: string | null = null
let connectPromise: Promise<void> | null = null
let refCount = 0

const normalizeNativeBrokerUrl = (rawUrl: string): string => {
  if (rawUrl.startsWith("ws://") || rawUrl.startsWith("wss://")) {
    return rawUrl
  }
  if (rawUrl.startsWith("http://")) {
    return `ws://${rawUrl.slice("http://".length)}`
  }
  if (rawUrl.startsWith("https://")) {
    return `wss://${rawUrl.slice("https://".length)}`
  }
  if (rawUrl.startsWith("/")) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:"
    return `${protocol}//${window.location.host}${rawUrl}`
  }
  return rawUrl
}

const normalizeSockJsUrl = (rawUrl: string): string => {
  if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
    return rawUrl
  }
  if (rawUrl.startsWith("ws://")) {
    return `http://${rawUrl.slice("ws://".length)}`
  }
  if (rawUrl.startsWith("wss://")) {
    return `https://${rawUrl.slice("wss://".length)}`
  }
  if (rawUrl.startsWith("/")) {
    return `${window.location.origin}${rawUrl}`
  }
  return rawUrl
}

const getConfiguredWsUrl = (): string => {
  const env = import.meta.env as { VITE_WS_URL?: string }
  const configuredUrl = env.VITE_WS_URL?.trim()
  const wsUrl = configuredUrl && configuredUrl.length > 0
    ? configuredUrl
    : DEFAULT_WS_URL
  return wsUrl.replace(/\/+$/, "")
}

const getNativeWsUrl = (): string => normalizeNativeBrokerUrl(getConfiguredWsUrl())

const getSockJsUrl = (): string => normalizeSockJsUrl(getConfiguredWsUrl())

const supportsNativeWebSocket = (): boolean =>
  typeof window !== "undefined" && typeof window.WebSocket !== "undefined"

const loadSockJsFactory = async () => {
  await import("@/polyfills")
  const sockJsModule = await import("sockjs-client")
  return sockJsModule.default
}

const createClient = async (token: string): Promise<Client> => {
  const nativeWsUrl = getNativeWsUrl()
  const baseConfig = {
    reconnectDelay: 5000,
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
  }
  if (supportsNativeWebSocket()) {
    const nextClient = new Client({
        ...baseConfig,
        brokerURL: nativeWsUrl,
      })
    nextClient.debug = () => undefined
    return nextClient
  }

  const SockJS = await loadSockJsFactory()
  const nextClient = new Client({
    ...baseConfig,
    webSocketFactory: () => new SockJS(getSockJsUrl()),
  })
  nextClient.debug = () => undefined
  return nextClient
}

const connect = async (token: string) => {
  if (client?.connected && token === activeToken) {
    return
  }
  if (connectPromise) {
    await connectPromise
    return
  }
  if (client) {
    await client.deactivate()
  }
  activeToken = token
  client = await createClient(token)
  connectPromise = new Promise((resolve, reject) => {
    if (!client) {
      reject(new Error("WebSocket client not initialized"))
      return
    }
    client.onConnect = () => {
      connectPromise = null
      resolve()
    }
    client.onStompError = (frame: IFrame) => {
      connectPromise = null
      reject(new Error(frame.headers.message || frame.body || "STOMP error"))
    }
    client.onWebSocketError = () => {
      connectPromise = null
      reject(new Error("WebSocket error"))
    }
  })
  client.activate()
  await connectPromise
}

const ensureConnected = (): Client => {
  if (!client?.connected) {
    throw new Error("WebSocket connection is not established")
  }
  return client
}

export const retainConnection = async (token: string) => {
  refCount += 1
  await connect(token)
  return async () => {
    refCount = Math.max(0, refCount - 1)
    if (refCount === 0) {
      await disconnect()
    }
  }
}

export const disconnect = async () => {
  if (!client) {
    activeToken = null
    return
  }
  await client.deactivate()
  client = null
  activeToken = null
  connectPromise = null
}

export const subscribe = <T>(
  destination: string,
  parser: MessageParser<T>,
  handler: (payload: T) => void,
): Promise<Unsubscribe> => {
  const activeClient = ensureConnected()
  const subscription = activeClient.subscribe(destination, (message: IMessage) => {
    handler(parser(message.body))
  })
  return Promise.resolve(() => {
    subscription.unsubscribe()
  })
}

export const publishJson = async (
  token: string,
  destination: string,
  payload: unknown,
) => {
  await connect(token)
  const activeClient = ensureConnected()
  activeClient.publish({
    destination,
    body: JSON.stringify(payload),
  })
}

export const publishText = async (
  token: string,
  destination: string,
  payload: string,
) => {
  await connect(token)
  const activeClient = ensureConnected()
  activeClient.publish({
    destination,
    body: payload,
  })
}



export const waitForMessage = async <T>(
  destination: string,
  parser: MessageParser<T>,
  timeoutMs = 10000,
): Promise<T> => {
  const activeClient = ensureConnected()
  return new Promise<T>((resolve, reject) => {
    let subscription: StompSubscription | null = null
    const timeout = setTimeout(() => {
      subscription?.unsubscribe()
      reject(new Error("Timed out waiting for WebSocket response"))
    }, timeoutMs)
    subscription = activeClient.subscribe(destination, (message: IMessage) => {
      clearTimeout(timeout)
      subscription?.unsubscribe()
      resolve(parser(message.body))
    })
  })
}
