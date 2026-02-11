/**
 * RTK Query WebSocket endpoints that map the backend game flow into
 * cache updates and slice state for real-time gameplay.
 */

import { baseApi } from "@/app/baseApi"
import type { FetchBaseQueryError } from "@reduxjs/toolkit/query"
import type { RootState } from "@/app/store"
import {
  clearActiveRoom,
  markAnswerProgress,
  resetGameState,
  setActiveRoomCode,
  setActiveRoomSnapshot,
  setCorrectAnswer,
  setCurrentQuestion,
  setEndGameReason,
  setFailedAnswer,
  setLastError,
  setResults,
  setSubmitAck,
} from "@/features/game/slice/gameSlice"
import {
  publishJson,
  retainConnection,
  subscribe,
  waitForMessage,
} from "@/features/game/api/gameSocket"
import type {
  AnswerProgressDto,
  AnswerSubmissionRequest,
  CorrectAnswerDto,
  EndGameEarlyRequest,
  GameRoomDto,
  GenerateQuizRequest,
  JoinRoomRequest,
  LeaveRoomRequest,
  QuestionDto,
  QuestionRequest,
  ResultsDto,
  ResultsRequest,
  StartGameRequest,
} from "@/types/api"

type RoomStreamMessage = GameRoomDto | QuestionDto | ResultsDto | "END_GAME_EARLY"

const selectToken = (state: RootState) => state.auth.token

const normalizeRoomCode = (roomCode: string) => roomCode.trim().toUpperCase()

const parseGameRoom = (body: string): GameRoomDto =>
  JSON.parse(body) as GameRoomDto

const parseAnswerProgress = (body: string): AnswerProgressDto =>
  JSON.parse(body) as AnswerProgressDto

const parseCorrectAnswer = (body: string): CorrectAnswerDto =>
  JSON.parse(body) as CorrectAnswerDto

const parseRoomStream = (body: string): RoomStreamMessage => {
  if (body === "END_GAME_EARLY") {
    return "END_GAME_EARLY"
  }
  try {
    return JSON.parse(body) as RoomStreamMessage
  } catch {
    return body as RoomStreamMessage
  }
}

const parseText = (body: string) => body

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null

const isGameRoomDto = (value: unknown): value is GameRoomDto =>
  isRecord(value) &&
  typeof value.roomCode === "string" &&
  Array.isArray(value.players)

const isQuestionDto = (value: unknown): value is QuestionDto =>
  isRecord(value) &&
  typeof value.questionId === "number" &&
  Array.isArray(value.answers)

const isResultsDto = (value: unknown): value is ResultsDto =>
  isRecord(value) && typeof value.endTime === "string" && Array.isArray(value.players)

const getToken = (getState: () => unknown) => {
  const token = selectToken(getState() as RootState)
  if (!token) {
    throw new Error("Missing auth token")
  }
  return token
}

const buildError = (error: unknown): FetchBaseQueryError => ({
  status: "CUSTOM_ERROR",
  error: error instanceof Error ? error.message : "Unknown WebSocket error",
})

const withRoomCode = <T extends { roomCode: string }>(payload: T): T => ({
  ...payload,
  roomCode: normalizeRoomCode(payload.roomCode),
})

export const gameApi = baseApi.injectEndpoints({
  endpoints: build => ({
    holdConnection: build.query<null, undefined>({
      queryFn: () => ({ data: null }),
      async onCacheEntryAdded(_arg, lifecycleApi) {
        const { cacheDataLoaded, cacheEntryRemoved, dispatch } = lifecycleApi
        let release: (() => Promise<void>) | null = null
        try {
          await cacheDataLoaded
          const token = getToken(() => lifecycleApi.getState())
          release = await retainConnection(token)
          await cacheEntryRemoved
        } catch (error) {
          dispatch(
            setLastError(
              error instanceof Error ? error.message : "Unknown WebSocket error",
            ),
          )
        } finally {
          if (release) {
            await release()
          }
        }
      },
    }),
    watchRoom: build.query<GameRoomDto | null, string>({
      queryFn: () => ({ data: null }),
      async onCacheEntryAdded(roomCodeArg, lifecycleApi) {
        const { cacheDataLoaded, cacheEntryRemoved, dispatch } = lifecycleApi
        const roomCode = normalizeRoomCode(roomCodeArg)
        const unsubscribers: (() => void)[] = []
        let release: (() => Promise<void>) | null = null
        try {
          await cacheDataLoaded
          const token = getToken(() => lifecycleApi.getState())
          release = await retainConnection(token)
          dispatch(setActiveRoomCode(roomCode))

          const handleRoomMessage = (payload: RoomStreamMessage) => {
            if (payload === "END_GAME_EARLY") {
              dispatch(setEndGameReason("END_GAME_EARLY"))
              return
            }
            if (isGameRoomDto(payload)) {
              lifecycleApi.updateCachedData(draft => {
                if (!draft) {
                  return payload
                }
                Object.assign(draft, payload)
                return draft
              })
              dispatch(setActiveRoomSnapshot(payload))
              return
            }
            if (isQuestionDto(payload)) {
              dispatch(setCurrentQuestion(payload))
              return
            }
            if (isResultsDto(payload)) {
              dispatch(setResults(payload))
            }
          }

          unsubscribers.push(
            await subscribe(
              `/topic/room/${roomCode}`,
              parseRoomStream,
              handleRoomMessage,
            ),
          )
          unsubscribers.push(
            await subscribe(
              `/topic/room/${roomCode}/progress`,
              parseAnswerProgress,
              payload => {
                dispatch(markAnswerProgress(payload))
              },
            ),
          )
          unsubscribers.push(
            await subscribe(
              `/topic/room/${roomCode}/reveal`,
              parseCorrectAnswer,
              payload => {
                dispatch(setCorrectAnswer(payload))
              },
            ),
          )
          unsubscribers.push(
            await subscribe("/user/queue/errors", parseText, payload => {
              dispatch(setLastError(payload))
            }),
          )
          unsubscribers.push(
            await subscribe("/user/queue/submitAck", parseText, payload => {
              dispatch(setSubmitAck(payload))
            }),
          )
          unsubscribers.push(
            await subscribe("/user/queue/failed_answer", parseText, payload => {
              dispatch(setFailedAnswer(payload))
            }),
          )
          unsubscribers.push(
            await subscribe("/user/queue/timeout", parseText, payload => {
              const timeoutRoomCode = normalizeRoomCode(payload || roomCode)
              dispatch(
                setLastError(
                  `Quiz generation timed out for room ${timeoutRoomCode}. Please try again.`,
                ),
              )
            }),
          )
          unsubscribers.push(
            await subscribe("/user/queue/left", parseText, payload => {
              if (normalizeRoomCode(payload) === roomCode) {
                dispatch(resetGameState())
                dispatch(clearActiveRoom())
              }
            }),
          )

          await cacheEntryRemoved
        } catch (error) {
          dispatch(
            setLastError(
              error instanceof Error ? error.message : "Unknown WebSocket error",
            ),
          )
        } finally {
          unsubscribers.forEach(unsubscribe => {
            unsubscribe()
          })
          if (release) {
            await release()
          }
        }
      },
    }),
    createRoom: build.mutation<GameRoomDto, undefined>({
      async queryFn(_arg, api) {
        let release: (() => Promise<void>) | null = null
        try {
          const token = getToken(api.getState)
          release = await retainConnection(token)
          const responsePromise = waitForMessage<GameRoomDto>(
            "/user/queue/created",
            parseGameRoom,
          )
          await publishJson(token, "/app/create", {})
          const room = await responsePromise
          api.dispatch(resetGameState())
          api.dispatch(setActiveRoomSnapshot(room))
          return { data: room }
        } catch (error) {
          return { error: buildError(error) }
        } finally {
          if (release) {
            await release()
          }
        }
      },
    }),
    joinRoom: build.mutation<GameRoomDto, JoinRoomRequest>({
      async queryFn(arg, api) {
        let release: (() => Promise<void>) | null = null
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          const fallbackJoinError = new Error(
            "Could not join room. Verify the room pin and try again.",
          )
          release = await retainConnection(token)
          const joinPromise = waitForMessage<GameRoomDto>(
            "/user/queue/joined",
            parseGameRoom,
          )
            .then(room => ({ kind: "joined" as const, room }))
            .catch((error: unknown) => ({
              kind: "join_error" as const,
              error,
            }))
          const userErrorPromise = waitForMessage<string>(
            "/user/queue/errors",
            parseText,
          ).then(message => ({ kind: "error" as const, message }))
          const queueErrorPromise = waitForMessage<string>(
            "/queue/errors",
            parseText,
          )
            .then(message => ({ kind: "error" as const, message }))
            .catch(() => ({ kind: "ignored" as const }))

          await publishJson(token, "/app/join", payload)

          const outcome = await Promise.race([
            joinPromise,
            userErrorPromise,
            queueErrorPromise,
          ])

          if (outcome.kind === "error") {
            throw new Error(outcome.message)
          }

          if (outcome.kind === "join_error") {
            if (
              outcome.error instanceof Error &&
              outcome.error.message.includes("Timed out")
            ) {
              throw fallbackJoinError
            }
            throw outcome.error
          }

          if (outcome.kind !== "joined") {
            throw fallbackJoinError
          }

          const room = outcome.room
          api.dispatch(resetGameState())
          api.dispatch(setActiveRoomSnapshot(room))
          return { data: room }
        } catch (error) {
          return { error: buildError(error) }
        } finally {
          if (release) {
            await release()
          }
        }
      },
    }),
    reconnectRoom: build.mutation<GameRoomDto | null, undefined>({
      async queryFn(_arg, api) {
        let release: (() => Promise<void>) | null = null
        try {
          const token = getToken(api.getState)
          release = await retainConnection(token)
          const responsePromise = waitForMessage<GameRoomDto>(
            "/user/queue/reconnected",
            parseGameRoom,
            4000,
          )
          await publishJson(token, "/app/reconnect", {})
          const room = await responsePromise
          api.dispatch(resetGameState())
          api.dispatch(setActiveRoomSnapshot(room))
          return { data: room }
        } catch (error) {
          if (error instanceof Error && error.message.includes("Timed out")) {
            api.dispatch(resetGameState())
            api.dispatch(clearActiveRoom())
            return { data: null }
          }
          return { error: buildError(error) }
        } finally {
          if (release) {
            await release()
          }
        }
      },
    }),
    leaveRoom: build.mutation<string, LeaveRoomRequest>({
      async queryFn(arg, api) {
        let release: (() => Promise<void>) | null = null
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          release = await retainConnection(token)
          const responsePromise = waitForMessage<string>(
            "/user/queue/left",
            parseText,
          )
          await publishJson(token, "/app/leave", payload)
          const response = await responsePromise
          api.dispatch(resetGameState())
          api.dispatch(clearActiveRoom())
          return { data: response }
        } catch (error) {
          return { error: buildError(error) }
        } finally {
          if (release) {
            await release()
          }
        }
      },
    }),
    generateQuiz: build.mutation<null, GenerateQuizRequest>({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          await publishJson(token, "/app/generate", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
    startGame: build.mutation<null, StartGameRequest>({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          await publishJson(token, "/app/startGame", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
    requestQuestion: build.mutation<null, QuestionRequest>({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          await publishJson(token, "/app/requestQuestion", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
    submitAnswer: build.mutation<
      null,
      Omit<AnswerSubmissionRequest, "submissionTime"> & {
        submissionTime?: string
      }
    >({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode({
            ...arg,
            submissionTime: arg.submissionTime ?? new Date().toISOString(),
          })
          await publishJson(token, "/app/submitAnswer", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
    endResults: build.mutation<null, ResultsRequest>({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          await publishJson(token, "/app/endResults", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
    endGame: build.mutation<null, EndGameEarlyRequest>({
      async queryFn(arg, api) {
        try {
          const token = getToken(api.getState)
          const payload = withRoomCode(arg)
          await publishJson(token, "/app/endGame", payload)
          return { data: null }
        } catch (error) {
          return { error: buildError(error) }
        }
      },
    }),
  }),
})

export const {
  useHoldConnectionQuery,
  useWatchRoomQuery,
  useCreateRoomMutation,
  useJoinRoomMutation,
  useReconnectRoomMutation,
  useLeaveRoomMutation,
  useGenerateQuizMutation,
  useStartGameMutation,
  useRequestQuestionMutation,
  useSubmitAnswerMutation,
  useEndResultsMutation,
  useEndGameMutation,
} = gameApi
