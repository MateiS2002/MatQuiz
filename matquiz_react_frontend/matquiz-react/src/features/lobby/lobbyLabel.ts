/**
 * Computes lobby header labels and actions from game phase + role.
 * Keeps UI text centralized and easy to update to match Figma.
 */
import type { GameStatus } from "@/types/api"

export type LobbyPhase =
  | "CREATING"
  | "JOIN"
  | "WAITING"
  | "GENERATING"
  | "READY"
  | "PLAYING"
  | "REVEAL"
  | "RESULTS"

export type LobbyTone = "neutral" | "success" | "danger"

export type LobbyAction = "exit" | "skip" | "end"

export type LobbyLabelInput = {
  isHost: boolean
  phase: LobbyPhase
  timeRemaining?: number | null
  answerResult?: "correct" | "wrong" | null
}

export type LobbyLabelOutput = {
  label: string
  tone: LobbyTone
  actions: LobbyAction[]
  hideProfileMenu: boolean
}

export const mapGameStatusToLobbyPhase = (status?: GameStatus): LobbyPhase => {
  switch (status) {
    case "GENERATING":
      return "GENERATING"
    case "READY":
      return "READY"
    case "PLAYING":
      return "PLAYING"
    case "FINISHED":
      return "RESULTS"
    case "WAITING":
    default:
      return "WAITING"
  }
}

const baseActions = (isHost: boolean, phase: LobbyPhase): LobbyAction[] => {
  if (phase === "PLAYING") {
    return isHost ? ["exit", "skip", "end"] : ["exit"]
  }
  return ["exit"]
}

export const resolveLobbyLabel = (
  input: LobbyLabelInput,
): LobbyLabelOutput => {
  const { isHost, phase, timeRemaining, answerResult } = input

  if (phase === "CREATING") {
    return {
      label: "Creating server room",
      tone: "neutral",
      actions: ["exit"],
      hideProfileMenu: false,
    }
  }

  if (phase === "JOIN") {
    return {
      label: "Join a room",
      tone: "neutral",
      actions: baseActions(isHost, phase),
      hideProfileMenu: false,
    }
  }

  if (phase === "WAITING") {
    return {
      label: isHost
        ? "The players are waiting for you"
        : "Waiting for host to start...",
      tone: "neutral",
      actions: baseActions(isHost, phase),
      hideProfileMenu: false,
    }
  }

  if (phase === "GENERATING") {
    return {
      label: "Quiz is being generated hang tight",
      tone: "neutral",
      actions: baseActions(isHost, phase),
      hideProfileMenu: false,
    }
  }

  if (phase === "READY") {
    return {
      label: "Get ready the quiz is prepared",
      tone: "neutral",
      actions: baseActions(isHost, phase),
      hideProfileMenu: false,
    }
  }

  if (phase === "PLAYING") {
    return {
      label:
        typeof timeRemaining === "number"
          ? `Time remaining: ${String(timeRemaining)} seconds`
          : "Game starting prepare...",
      tone: "neutral",
      actions: baseActions(isHost, phase),
      hideProfileMenu: true,
    }
  }

  if (phase === "REVEAL") {
    const isCorrect = answerResult === "correct"
    return {
      label: isCorrect ? "Correct answer :)" : "Wrong answer :(",
      tone: isCorrect ? "success" : "danger",
      actions: baseActions(isHost, phase),
      hideProfileMenu: true,
    }
  }

  return {
    label: "Results",
    tone: "neutral",
    actions: baseActions(isHost, phase),
    hideProfileMenu: true,
  }
}
