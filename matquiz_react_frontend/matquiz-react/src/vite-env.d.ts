/// <reference types="vite/client" />

declare const __APP_VERSION__: string

type ImportMetaEnv = {
  readonly VITE_API_URL?: string
  readonly VITE_WS_URL?: string
}

type ImportMeta = {
  readonly env: ImportMetaEnv
}
