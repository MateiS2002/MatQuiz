import type { GamePlayerDto } from "@/types/api"

export type RuntimeStage =
  | "LOBBY"
  | "START_COUNTDOWN"
  | "QUESTION"
  | "SKIP_COUNTDOWN"
  | "REVEAL"
  | "RESULTS"

export type RankedPlayer = {
  player: GamePlayerDto
  rank: number
}
