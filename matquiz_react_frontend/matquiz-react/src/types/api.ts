/**
 * Central API contract types shared across REST and WebSocket layers.
 * Mirrors backend DTOs to keep the client strongly typed and consistent.
 */

export type Difficulty = "EASY" | "ADVANCED"

export type GameStatus = "WAITING" | "GENERATING" | "READY" | "PLAYING" | "FINISHED"

export type Role = "ROLE_USER" | "ROLE_ADMIN"

export type LoginRequest = {
  username: string
  password: string
}

export type RegisterRequest = {
  username: string
  password: string
  email: string
}

export type SetProfilePictureRequest = {
  avatarUrl: string
}

export type ChangeUsernameRequest = {
  newUsername: string
}

export type ChangeEmailRequest = {
  newEmail: string
}

export type ChangePasswordRequest = {
  currentPassword: string
  newPassword: string
}

export type LeaderboardRequest = {
  username: string
}

export type LeaderboardDto = {
  rank: number
  username: string
  eloRating: number
}

export type UserSummaryDto = {
  id: number
  username: string
  email: string
  role: Role
  eloRating: number
  lastGamePoints: number
  avatarUrl?: string
}

export type SessionRefreshDto = {
  accessToken: string
  user: UserSummaryDto
}

export type ActiveGameDto = {
  hasActiveGame: boolean
}

export type GamePlayerDto = {
  nickname: string
  score: number
  isConnected: boolean
  avatarUrl?: string
}

export type GameRoomDto = {
  roomCode: string
  topic: string
  difficulty: Difficulty
  status: GameStatus
  host: UserSummaryDto
  players: GamePlayerDto[]
}

export type QuestionDto = {
  questionId: number
  question_text: string
  answers: string[]
  order_index: number
}

export type CorrectAnswerDto = {
  questionId: number
  correctAnswer: number
}

export type AnswerProgressDto = {
  nickname: string
  answered: boolean
}

export type ResultsDto = {
  endTime: string
  players: GamePlayerDto[]
}

export type CreateRoomRequest = Record<string, never>

export type JoinRoomRequest = {
  roomCode: string
}

export type LeaveRoomRequest = {
  roomCode: string
}

export type GenerateQuizRequest = {
  roomCode: string
  topic: string
  difficulty: Difficulty
}

export type StartGameRequest = {
  roomCode: string
}

export type QuestionRequest = {
  roomCode: string
}

export type AnswerSubmissionRequest = {
  roomCode: string
  questionId: number
  selectedAnswerIndex: number
  submissionTime: string
}

export type ResultsRequest = {
  roomCode: string
}

export type EndGameEarlyRequest = {
  roomCode: string
}
