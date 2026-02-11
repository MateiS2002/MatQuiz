/**
 * Centralizes transient gameplay state that is driven by WebSocket messages,
 * keeping question flow, answer progress, and error signals in one slice.
 */

import { createSlice } from "@reduxjs/toolkit"
import type {
  AnswerProgressDto,
  CorrectAnswerDto,
  GameRoomDto,
  QuestionDto,
  ResultsDto,
} from "@/types/api"

type GameState = {
  activeRoomCode: string | null
  activeRoomSnapshot: GameRoomDto | null
  lobbyHeader: string | null
  currentQuestion: QuestionDto | null
  answerProgress: Record<string, boolean>
  correctAnswer: CorrectAnswerDto | null
  results: ResultsDto | null
  lastError: string | null
  lastSubmitAck: string | null
  lastFailedAnswer: string | null
  endGameReason: "END_GAME_EARLY" | null
}

const initialState: GameState = {
  activeRoomCode: null,
  activeRoomSnapshot: null,
  lobbyHeader: null,
  currentQuestion: null,
  answerProgress: {},
  correctAnswer: null,
  results: null,
  lastError: null,
  lastSubmitAck: null,
  lastFailedAnswer: null,
  endGameReason: null,
}

export const gameSlice = createSlice({
  name: "game",
  initialState,
  reducers: {
    setActiveRoomCode: (state, action: { payload: string }) => {
      state.activeRoomCode = action.payload
    },
    clearActiveRoom: state => {
      state.activeRoomCode = null
      state.activeRoomSnapshot = null
    },
    setActiveRoomSnapshot: (state, action: { payload: GameRoomDto }) => {
      state.activeRoomSnapshot = action.payload
      state.activeRoomCode = action.payload.roomCode
    },
    clearActiveRoomSnapshot: state => {
      state.activeRoomSnapshot = null
    },
    setLobbyHeader: (state, action: { payload: string }) => {
      state.lobbyHeader = action.payload
    },
    clearLobbyHeader: state => {
      state.lobbyHeader = null
    },
    setCurrentQuestion: (state, action: { payload: QuestionDto | null }) => {
      state.currentQuestion = action.payload
      state.correctAnswer = null
      state.answerProgress = {}
    },
    resetAnswerProgress: state => {
      state.answerProgress = {}
    },
    markAnswerProgress: (state, action: { payload: AnswerProgressDto }) => {
      state.answerProgress[action.payload.nickname] = action.payload.answered
    },
    setCorrectAnswer: (state, action: { payload: CorrectAnswerDto }) => {
      state.correctAnswer = action.payload
    },
    setResults: (state, action: { payload: ResultsDto }) => {
      state.results = action.payload
    },
    setLastError: (state, action: { payload: string }) => {
      state.lastError = action.payload
    },
    clearLastError: state => {
      state.lastError = null
    },
    setSubmitAck: (state, action: { payload: string }) => {
      state.lastSubmitAck = action.payload
    },
    setFailedAnswer: (state, action: { payload: string }) => {
      state.lastFailedAnswer = action.payload
    },
    setEndGameReason: (state, action: { payload: "END_GAME_EARLY" }) => {
      state.endGameReason = action.payload
    },
    resetGameState: () => initialState,
  },
})

export const {
  setActiveRoomCode,
  clearActiveRoom,
  setActiveRoomSnapshot,
  clearActiveRoomSnapshot,
  setLobbyHeader,
  clearLobbyHeader,
  setCurrentQuestion,
  resetAnswerProgress,
  markAnswerProgress,
  setCorrectAnswer,
  setResults,
  setLastError,
  clearLastError,
  setSubmitAck,
  setFailedAnswer,
  setEndGameReason,
  resetGameState,
} = gameSlice.actions
