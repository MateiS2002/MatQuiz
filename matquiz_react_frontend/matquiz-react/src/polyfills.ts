/**
 * Polyfills globals expected by some browser libraries (e.g., SockJS).
 */

type GlobalWithOptionalGlobal = typeof globalThis & { global?: typeof globalThis }

const globalRef: GlobalWithOptionalGlobal = globalThis

globalRef.global = globalRef
