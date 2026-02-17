import type { RankedPlayer } from "@/features/lobby/runtimeTypes"
import type { GamePlayerDto } from "@/types/api"

export const buildSlots = (players: GamePlayerDto[], count: number) => {
  const slots: (GamePlayerDto | null)[] = []
  for (let index = 0; index < count; index += 1) {
    slots.push(players[index] ?? null)
  }
  return slots
}

const stripControlChars = (value: string) =>
  Array.from(value)
    .map(char => {
      const code = char.charCodeAt(0)
      if (code < 32 || code === 127) {
        return " "
      }
      return char
    })
    .join("")

export const normalizeTopic = (value: string) =>
  stripControlChars(value)
    .replace(/\s+/g, " ")
    .trim()

export const capitalizeFirstLetter = (value: string) => {
  if (!value) {
    return value
  }
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`
}

export const parseSocketError = (value: unknown, fallback: string) => {
  if (typeof value === "object" && value !== null) {
    const maybeError = value as { error?: string; data?: unknown }
    if (typeof maybeError.error === "string") {
      return maybeError.error
    }
    if (typeof maybeError.data === "string") {
      return maybeError.data
    }
  }
  return fallback
}

export const rankPlayers = (players: GamePlayerDto[]): RankedPlayer[] => {
  const sorted = [...players].sort((left, right) => {
    if (right.score === left.score) {
      return left.nickname.localeCompare(right.nickname)
    }
    return right.score - left.score
  })

  let activeRank = 0
  let previousScore: number | null = null

  return sorted.map((player, index) => {
    if (previousScore === null || player.score < previousScore) {
      activeRank = index + 1
      previousScore = player.score
    }
    return {
      player,
      rank: activeRank,
    }
  })
}
