import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type SyntheticEvent,
} from "react"
import { useNavigate } from "react-router-dom"
import { useAppDispatch, useAppSelector } from "@/app/hooks"
import InfoModal from "@/components/InfoModal"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import {
  useEndResultsMutation,
  useGenerateQuizMutation,
  useRequestQuestionMutation,
  useStartGameMutation,
  useSubmitAnswerMutation,
  useWatchRoomQuery,
} from "@/features/game/api/gameApiSlice"
import {
  clearLastError,
  clearLobbyHeader,
  setLobbyHeader,
} from "@/features/game/slice/gameSlice"
import LobbyHostSetupView from "@/features/lobby/components/LobbyHostSetupView"
import LobbyPlayerWaitingView from "@/features/lobby/components/LobbyPlayerWaitingView"
import LobbyRuntimeView from "@/features/lobby/components/LobbyRuntimeView"
import { resolveLobbyLabel, type LobbyPhase } from "@/features/lobby/lobbyLabel"
import type { RankedPlayer, RuntimeStage } from "@/features/lobby/runtimeTypes"
import { ROUTES } from "@/routes/paths"
import type { Difficulty, GamePlayerDto, GameStatus, QuestionDto } from "@/types/api"
import styles from "./Lobby.module.css"

const MAX_TOPIC_LENGTH = 30
const START_COUNTDOWN_SECONDS = 5
const QUESTION_SECONDS = 30
const REVEAL_VISIBILITY_DELAY_MS = 1000
const AUTO_NEXT_SECONDS = 8
const MANUAL_NEXT_FALLBACK_SECONDS = 10
const SKIP_COUNTDOWN_SECONDS = 4
const END_RESULTS_DELAY_MS = 5000
const GENERATING_FACTS = [
  "A day on Venus is longer than a Venus year.",
  "Octopuses have three hearts and blue blood.",
  "Bananas are berries, but strawberries are not.",
  "Honey does not spoil when kept sealed.",
  "The Eiffel Tower grows taller in summer heat.",
] as const

const mapStatusToPhase = (status?: GameStatus): LobbyPhase => {
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

const buildSlots = (players: GamePlayerDto[], count: number) => {
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

const normalizeTopic = (value: string) =>
  stripControlChars(value)
    .replace(/\s+/g, " ")
    .trim()

const capitalizeFirstLetter = (value: string) => {
  if (!value) {
    return value
  }
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`
}

const parseSocketError = (value: unknown, fallback: string) => {
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

const rankPlayers = (players: GamePlayerDto[]): RankedPlayer[] => {
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

const Lobby = () => {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const { data: user, refetch: refetchSession } = useGetSessionQuery(undefined)
  const activeRoomCode = useAppSelector(state => state.game.activeRoomCode)
  const activeRoomSnapshot = useAppSelector(
    state => state.game.activeRoomSnapshot,
  )
  const currentQuestion = useAppSelector(state => state.game.currentQuestion)
  const answerProgress = useAppSelector(state => state.game.answerProgress)
  const correctAnswer = useAppSelector(state => state.game.correctAnswer)
  const results = useAppSelector(state => state.game.results)
  const endGameReason = useAppSelector(state => state.game.endGameReason)
  const lastError = useAppSelector(state => state.game.lastError)
  const { data: streamedRoom } = useWatchRoomQuery(activeRoomCode ?? "", {
    skip: !activeRoomCode,
  })
  const [generateQuiz, { isLoading: isGenerating }] =
    useGenerateQuizMutation()
  const [startGame, { isLoading: isStartingGame }] = useStartGameMutation()
  const [requestQuestion, { isLoading: isRequestingQuestion }] =
    useRequestQuestionMutation()
  const [submitAnswer] = useSubmitAnswerMutation()
  const [endResults, { isLoading: isFetchingEndResults }] =
    useEndResultsMutation()

  const [topic, setTopic] = useState("")
  const [difficulty, setDifficulty] = useState<Difficulty>("EASY")
  const [modalMessage, setModalMessage] = useState<string | null>(null)
  const [consoleLines, setConsoleLines] = useState<string[]>([])
  const [stage, setStage] = useState<RuntimeStage>("LOBBY")
  const [startCountdown, setStartCountdown] = useState(START_COUNTDOWN_SECONDS)
  const [skipCountdown, setSkipCountdown] = useState(SKIP_COUNTDOWN_SECONDS)
  const [questionCountdown, setQuestionCountdown] = useState(QUESTION_SECONDS)
  const [revealElapsed, setRevealElapsed] = useState(0)
  const [selectedAnswerIndex, setSelectedAnswerIndex] = useState<number | null>(
    null,
  )
  const [lockedAnswerIndex, setLockedAnswerIndex] = useState<number | null>(null)
  const [isSubmittingAnswer, setIsSubmittingAnswer] = useState(false)
  const [displayedQuestion, setDisplayedQuestion] = useState<QuestionDto | null>(
    null,
  )
  const [pendingQuestion, setPendingQuestion] = useState<QuestionDto | null>(null)
  const [revealedQuestionId, setRevealedQuestionId] = useState<number | null>(null)
  const [revealedCorrectIndex, setRevealedCorrectIndex] = useState<number | null>(
    null,
  )
  const [lastQuestionOutcome, setLastQuestionOutcome] = useState<
    "correct" | "wrong" | null
  >(null)
  const generationFactRef = useRef<string | null>(null)
  const startFlowTriggeredRef = useRef(false)
  const autoNextRequestedForQuestionIdRef = useRef<number | null>(null)
  const autoEndResultsRequestedRef = useRef(false)
  const revealDelayTimeoutRef = useRef<number | null>(null)

  const room = streamedRoom ?? activeRoomSnapshot
  const roomStatus = room?.status ?? "WAITING"
  const roomCode = room?.roomCode ?? activeRoomCode ?? ""
  const hostName = room?.host.username
  const userName = user?.username
  const isHost = Boolean(hostName && userName && hostName === userName)
  const displayRoomCode = roomCode.length > 0 ? roomCode : "-----"
  const displayUserName = userName ?? "Player"
  const runtimePlayers = useMemo(
    () => results?.players ?? room?.players ?? [],
    [results?.players, room?.players],
  )
  const rankedPlayers = useMemo(
    () => rankPlayers(runtimePlayers),
    [runtimePlayers],
  )
  const rankedSlots = useMemo(() => {
    const slots: (RankedPlayer | null)[] = [...rankedPlayers]
    while (slots.length < 5) {
      slots.push(null)
    }
    return slots.slice(0, 5)
  }, [rankedPlayers])
  const yourPlacement = rankedPlayers.find(entry => entry.player.nickname === userName)
  const yourScore =
    runtimePlayers.find(player => player.nickname === userName)?.score ?? 0
  const answeredPlayerNames = useMemo(
    () =>
      Object.entries(answerProgress)
        .filter(([, answered]) => answered)
        .map(([nickname]) => nickname),
    [answerProgress],
  )

  const hostPlayer = useMemo(() => {
    if (!room?.players.length || !hostName) {
      return null
    }
    return room.players.find(player => player.nickname === hostName) ?? null
  }, [hostName, room?.players])

  const youPlayer = useMemo(() => {
    if (!room?.players.length || !userName) {
      return null
    }
    return room.players.find(player => player.nickname === userName) ?? null
  }, [room?.players, userName])

  const otherPlayers = useMemo(() => {
    if (!room?.players.length) {
      return []
    }
    return room.players.filter(
      player =>
        player.nickname !== userName && player.nickname !== hostName,
    )
  }, [hostName, room?.players, userName])

  const hostSlots = useMemo(() => {
    const playersWithoutHost = room?.players.filter(
      player => player.nickname !== hostName,
    )
    return buildSlots(playersWithoutHost ?? [], 4)
  }, [hostName, room?.players])

  const playerSlots = useMemo(() => buildSlots(otherPlayers, 3), [otherPlayers])
  const normalizedTopic = useMemo(() => normalizeTopic(topic), [topic])
  const selectedTopic = room?.topic ?? normalizedTopic
  const selectedTopicLabel = useMemo(
    () => capitalizeFirstLetter(selectedTopic),
    [selectedTopic],
  )
  const selectedDifficulty = room?.difficulty ?? difficulty
  const isRoomWaiting = roomStatus === "WAITING"
  const isRoomGenerating = roomStatus === "GENERATING"
  const isRoomReady = roomStatus === "READY"
  const isRoomPlaying = roomStatus === "PLAYING"
  const isRoomFinished = roomStatus === "FINISHED"
  const isRuntimeView = isRoomPlaying || isRoomFinished || results !== null
  const isLockedGenerationState = roomStatus !== "WAITING"
  const canStartGame = !!roomCode && isRoomReady && !isStartingGame

  const canGenerate =
    normalizedTopic.length > 0 &&
    normalizedTopic.length <= MAX_TOPIC_LENGTH &&
    !!roomCode &&
    isRoomWaiting &&
    !isGenerating

  const questionProgressPercent = useMemo(() => {
    if (!displayedQuestion) {
      return 0
    }
    return Math.min(
      100,
      Math.max(0, ((QUESTION_SECONDS - questionCountdown) / QUESTION_SECONDS) * 100),
    )
  }, [displayedQuestion, questionCountdown])

  const revealProgressPercent = useMemo(() => {
    if (!displayedQuestion) {
      return 0
    }
    return Math.min(
      100,
      Math.max(
        0,
        ((AUTO_NEXT_SECONDS - revealElapsed) / AUTO_NEXT_SECONDS) * 100,
      ),
    )
  }, [displayedQuestion, revealElapsed])

  const activateQuestion = useCallback((question: QuestionDto) => {
    if (revealDelayTimeoutRef.current !== null) {
      window.clearTimeout(revealDelayTimeoutRef.current)
      revealDelayTimeoutRef.current = null
    }
    setDisplayedQuestion(question)
    setPendingQuestion(null)
    setStage("QUESTION")
    setQuestionCountdown(QUESTION_SECONDS)
    setSelectedAnswerIndex(null)
    setLockedAnswerIndex(null)
    setIsSubmittingAnswer(false)
    setLastQuestionOutcome(null)
    setRevealedCorrectIndex(null)
    setRevealElapsed(0)
    autoNextRequestedForQuestionIdRef.current = null
  }, [])

  const requestNextQuestion = useCallback(
    async (fallback: string) => {
      if (!isHost || !roomCode) {
        return
      }
      await requestQuestion({ roomCode })
        .unwrap()
        .catch((error: unknown) => {
          throw new Error(parseSocketError(error, fallback))
        })
    },
    [isHost, requestQuestion, roomCode],
  )

  const lockAnswer = useCallback(
    async (answerIndex: number) => {
      if (
        !displayedQuestion ||
        !roomCode ||
        lockedAnswerIndex !== null ||
        isSubmittingAnswer
      ) {
        return
      }

      setLockedAnswerIndex(answerIndex)
      setIsSubmittingAnswer(true)

      await submitAnswer({
        roomCode,
        questionId: displayedQuestion.questionId,
        selectedAnswerIndex: answerIndex,
      })
        .unwrap()
        .catch((error: unknown) => {
          setLockedAnswerIndex(null)
          throw new Error(
            parseSocketError(error, "Could not lock answer. Please try again."),
          )
        })
        .finally(() => {
          setIsSubmittingAnswer(false)
        })
    },
    [displayedQuestion, isSubmittingAnswer, lockedAnswerIndex, roomCode, submitAnswer],
  )

  useEffect(() => {
    return () => {
      if (revealDelayTimeoutRef.current !== null) {
        window.clearTimeout(revealDelayTimeoutRef.current)
      }
    }
  }, [])

  useEffect(() => {
    return () => {
      dispatch(clearLobbyHeader())
    }
  }, [dispatch])

  useEffect(() => {
    if (!activeRoomCode) {
      void navigate(ROUTES.gameControl)
    }
  }, [activeRoomCode, navigate])

  useEffect(() => {
    if (endGameReason !== "END_GAME_EARLY") {
      return
    }
    void navigate(ROUTES.gameControl)
  }, [endGameReason, navigate])

  useEffect(() => {
    if (!lastError) {
      return
    }
    setModalMessage(lastError)
    dispatch(clearLastError())
  }, [dispatch, lastError])

  useEffect(() => {
    if (!isHost || !isLockedGenerationState) {
      setConsoleLines([])
      generationFactRef.current = null
      return
    }

    if (isRoomReady) {
      generationFactRef.current ??=
        GENERATING_FACTS[Math.floor(Math.random() * GENERATING_FACTS.length)]
      const readyLines = ["Quiz generated successfully.", "Good luck, have fun!"]
      readyLines.push(`Fun fact: ${generationFactRef.current}`)
      setConsoleLines(readyLines)
      return
    }

    if (!isRoomGenerating) {
      setConsoleLines(["Quiz is active. Waiting for host actions..."])
      return
    }

    generationFactRef.current ??=
      GENERATING_FACTS[Math.floor(Math.random() * GENERATING_FACTS.length)]

    setConsoleLines([
      "Connected to server api.google.com",
      "Generating quiz using Gemini...",
      `Fun fact: ${generationFactRef.current}`,
    ])
  }, [isHost, isLockedGenerationState, isRoomGenerating, isRoomReady])

  useEffect(() => {
    if (isRoomPlaying) {
      if (stage === "RESULTS") {
        setStage("QUESTION")
      }
      return
    }

    if (isRoomFinished) {
      setStage(results ? "RESULTS" : "FINISHED")
      return
    }

    setStage("LOBBY")
    setDisplayedQuestion(null)
    setPendingQuestion(null)
    setSelectedAnswerIndex(null)
    setLockedAnswerIndex(null)
    setRevealedQuestionId(null)
    setRevealedCorrectIndex(null)
    setLastQuestionOutcome(null)
    setStartCountdown(START_COUNTDOWN_SECONDS)
    setSkipCountdown(SKIP_COUNTDOWN_SECONDS)
    setQuestionCountdown(QUESTION_SECONDS)
    setRevealElapsed(0)
    startFlowTriggeredRef.current = false
    autoNextRequestedForQuestionIdRef.current = null
    autoEndResultsRequestedRef.current = false
  }, [isRoomFinished, isRoomPlaying, results, stage])

  useEffect(() => {
    if (!isRoomPlaying || displayedQuestion || pendingQuestion) {
      return
    }
    setStage("START_COUNTDOWN")
    setStartCountdown(START_COUNTDOWN_SECONDS)
  }, [displayedQuestion, isRoomPlaying, pendingQuestion])

  useEffect(() => {
    if (stage !== "START_COUNTDOWN") {
      return
    }

    if (startCountdown <= 0) {
      if (!isHost || !roomCode || startFlowTriggeredRef.current) {
        return
      }
      startFlowTriggeredRef.current = true
      void requestNextQuestion("Could not request the first question.")
        .catch((error: unknown) => {
          startFlowTriggeredRef.current = false
          setModalMessage(error instanceof Error ? error.message : String(error))
        })
      return
    }

    const timer = window.setTimeout(() => {
      setStartCountdown(previous => Math.max(0, previous - 1))
    }, 1000)

    return () => {
      window.clearTimeout(timer)
    }
  }, [isHost, requestNextQuestion, roomCode, stage, startCountdown])

  useEffect(() => {
    if (!currentQuestion) {
      return
    }

    if (
      displayedQuestion?.questionId === currentQuestion.questionId ||
      pendingQuestion?.questionId === currentQuestion.questionId
    ) {
      return
    }

    if (
      displayedQuestion &&
      revealedQuestionId !== displayedQuestion.questionId
    ) {
      setPendingQuestion(currentQuestion)
      setStage("SKIP_COUNTDOWN")
      setSkipCountdown(SKIP_COUNTDOWN_SECONDS)
      return
    }

    activateQuestion(currentQuestion)
    startFlowTriggeredRef.current = true
  }, [
    activateQuestion,
    currentQuestion,
    displayedQuestion,
    pendingQuestion,
    revealedQuestionId,
  ])

  useEffect(() => {
    if (stage !== "SKIP_COUNTDOWN") {
      return
    }

    if (skipCountdown <= 0) {
      if (pendingQuestion) {
        activateQuestion(pendingQuestion)
      }
      return
    }

    const timer = window.setTimeout(() => {
      setSkipCountdown(previous => Math.max(0, previous - 1))
    }, 1000)

    return () => {
      window.clearTimeout(timer)
    }
  }, [activateQuestion, pendingQuestion, skipCountdown, stage])

  useEffect(() => {
    if (stage !== "QUESTION" || !displayedQuestion) {
      return
    }

    if (questionCountdown <= 0) {
      if (
        selectedAnswerIndex !== null &&
        lockedAnswerIndex === null &&
        !isSubmittingAnswer
      ) {
        void lockAnswer(selectedAnswerIndex)
          .catch((error: unknown) => {
            setModalMessage(error instanceof Error ? error.message : String(error))
          })
      }
      return
    }

    const timer = window.setTimeout(() => {
      setQuestionCountdown(previous => Math.max(0, previous - 1))
    }, 1000)

    return () => {
      window.clearTimeout(timer)
    }
  }, [
    displayedQuestion,
    isSubmittingAnswer,
    lockAnswer,
    lockedAnswerIndex,
    questionCountdown,
    selectedAnswerIndex,
    stage,
  ])

  useEffect(() => {
    if (!correctAnswer || !displayedQuestion) {
      return
    }
    if (pendingQuestion) {
      return
    }
    if (correctAnswer.questionId !== displayedQuestion.questionId) {
      return
    }
    if (revealedQuestionId === correctAnswer.questionId) {
      return
    }

    if (revealDelayTimeoutRef.current !== null) {
      window.clearTimeout(revealDelayTimeoutRef.current)
    }

    setRevealedQuestionId(correctAnswer.questionId)
    setRevealedCorrectIndex(correctAnswer.correctAnswer)
    setLastQuestionOutcome(
      lockedAnswerIndex !== null && lockedAnswerIndex === correctAnswer.correctAnswer
        ? "correct"
        : "wrong",
    )

    revealDelayTimeoutRef.current = window.setTimeout(() => {
      setStage("REVEAL")
      setRevealElapsed(0)
      revealDelayTimeoutRef.current = null
    }, REVEAL_VISIBILITY_DELAY_MS)
  }, [
    correctAnswer,
    displayedQuestion,
    lockedAnswerIndex,
    pendingQuestion,
    revealedQuestionId,
  ])

  useEffect(() => {
    if (stage !== "REVEAL" || !displayedQuestion) {
      return
    }

    const timer = window.setInterval(() => {
      setRevealElapsed(previous => previous + 1)
    }, 1000)

    return () => {
      window.clearInterval(timer)
    }
  }, [displayedQuestion, stage])

  useEffect(() => {
    if (
      stage !== "REVEAL" ||
      !isHost ||
      !displayedQuestion ||
      revealElapsed < AUTO_NEXT_SECONDS ||
      !isRoomPlaying
    ) {
      return
    }

    if (autoNextRequestedForQuestionIdRef.current === displayedQuestion.questionId) {
      return
    }

    autoNextRequestedForQuestionIdRef.current = displayedQuestion.questionId
    void requestNextQuestion("Could not request the next question automatically.")
      .catch((error: unknown) => {
        autoNextRequestedForQuestionIdRef.current = null
        setModalMessage(error instanceof Error ? error.message : String(error))
      })
  }, [
    displayedQuestion,
    isHost,
    isRoomPlaying,
    requestNextQuestion,
    revealElapsed,
    stage,
  ])

  useEffect(() => {
    if (!isRoomFinished || !isHost || !!results || !roomCode) {
      return
    }
    if (autoEndResultsRequestedRef.current) {
      return
    }

    const timer = window.setTimeout(() => {
      autoEndResultsRequestedRef.current = true
      void endResults({ roomCode })
        .unwrap()
        .catch((error: unknown) => {
          autoEndResultsRequestedRef.current = false
          setModalMessage(
            parseSocketError(error, "Could not request final results."),
          )
        })
    }, END_RESULTS_DELAY_MS)

    return () => {
      window.clearTimeout(timer)
    }
  }, [endResults, isHost, isRoomFinished, results, roomCode])

  useEffect(() => {
    if (stage !== "RESULTS") {
      return
    }
    void refetchSession()
  }, [refetchSession, stage])

  const lobbyHeaderLabel = useMemo(() => {
    if (isRuntimeView) {
      if (stage === "START_COUNTDOWN") {
        return `Game starting prepare... ${String(startCountdown)}`
      }
      if (stage === "SKIP_COUNTDOWN") {
        return `Host skipped this question. Next one starts in ${String(skipCountdown)}`
      }
      if (stage === "QUESTION") {
        return `Time remaining: ${String(questionCountdown)} seconds`
      }
      if (stage === "REVEAL") {
        return lastQuestionOutcome === "correct"
          ? "Correct answer :)"
          : "Wrong answer :("
      }
      if (stage === "RESULTS") {
        return "Results"
      }
      return "Game finished. Preparing final results..."
    }

    const phase = mapStatusToPhase(room?.status)
    return resolveLobbyLabel({
      isHost,
      phase,
    }).label
  }, [
    isHost,
    isRuntimeView,
    lastQuestionOutcome,
    questionCountdown,
    room?.status,
    skipCountdown,
    stage,
    startCountdown,
  ])

  useEffect(() => {
    dispatch(setLobbyHeader(lobbyHeaderLabel))
  }, [dispatch, lobbyHeaderLabel])

  const handleGenerate = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isRoomReady) {
      if (!canStartGame) {
        return
      }
      void startGame({ roomCode })
        .unwrap()
        .catch((error: unknown) => {
          setModalMessage(
            parseSocketError(error, "Could not start the game. Please retry."),
          )
        })
      return
    }
    if (!isRoomWaiting) {
      setModalMessage("Quiz generation is in progress. Please wait.")
      return
    }
    if (normalizedTopic.length === 0) {
      setModalMessage("Topic is required.")
      return
    }
    if (normalizedTopic.length > MAX_TOPIC_LENGTH) {
      setModalMessage("Topic cannot be longer than 30 characters!")
      return
    }
    if (!canGenerate) {
      return
    }
    void generateQuiz({
      roomCode,
      topic: normalizedTopic,
      difficulty,
    })
      .unwrap()
      .catch((error: unknown) => {
        setModalMessage(
          parseSocketError(error, "Could not generate quiz. Please try again."),
        )
      })
  }

  const handleManualNext = () => {
    void requestNextQuestion("Could not request the next question.")
      .catch((error: unknown) => {
        setModalMessage(error instanceof Error ? error.message : String(error))
      })
  }

  const handleLockAnswer = () => {
    if (selectedAnswerIndex === null) {
      return
    }
    void lockAnswer(selectedAnswerIndex)
      .catch((error: unknown) => {
        setModalMessage(error instanceof Error ? error.message : String(error))
      })
  }

  const canUseManualNextFallback =
    isHost &&
    stage === "REVEAL" &&
    revealElapsed >= MANUAL_NEXT_FALLBACK_SECONDS &&
    isRoomPlaying &&
    !isRequestingQuestion
  const isHostSetupView = !isRuntimeView && isHost

  return (
    <div className={styles.lobby}>
      <div
        className={`${styles.darkContainer} ${
          isRuntimeView ? styles.darkContainerGameFlow : ""
        } ${isHostSetupView ? styles.darkContainerHostSetup : ""}`}
      >
        <div
          className={`${styles.panel} ${isRuntimeView ? styles.panelGameFlow : ""} ${
            stage === "RESULTS" ? styles.panelGameFlowResults : ""
          } ${isHostSetupView ? styles.panelHostSetup : ""}`}
        >
          {isRuntimeView ? (
            <LobbyRuntimeView
              stage={stage}
              isHost={isHost}
              hostName={hostName}
              currentEloRating={user?.eloRating}
              selectedTopicLabel={selectedTopicLabel}
              selectedDifficulty={selectedDifficulty}
              startCountdown={startCountdown}
              skipCountdown={skipCountdown}
              questionProgressPercent={questionProgressPercent}
              revealProgressPercent={revealProgressPercent}
              displayedQuestion={displayedQuestion}
              selectedAnswerIndex={selectedAnswerIndex}
              lockedAnswerIndex={lockedAnswerIndex}
              isSubmittingAnswer={isSubmittingAnswer}
              revealedCorrectIndex={revealedCorrectIndex}
              rankedPlayers={rankedPlayers}
              rankedSlots={rankedSlots}
              userName={userName}
              yourPlacement={yourPlacement}
              revealOutcome={lastQuestionOutcome}
              canUseManualNextFallback={canUseManualNextFallback}
              runtimePlayers={runtimePlayers}
              answeredPlayerNames={answeredPlayerNames}
              displayRoomCode={displayRoomCode}
              yourScore={yourScore}
              isFetchingEndResults={isFetchingEndResults}
              onSelectAnswer={setSelectedAnswerIndex}
              onLockAnswer={handleLockAnswer}
              onManualNext={handleManualNext}
            />
          ) : isHost ? (
            <LobbyHostSetupView
              hostSlots={hostSlots}
              isLockedGenerationState={isLockedGenerationState}
              selectedTopicLabel={selectedTopicLabel}
              selectedDifficulty={selectedDifficulty}
              consoleLines={consoleLines}
              isRoomGenerating={isRoomGenerating}
              isRoomReady={isRoomReady}
              topic={topic}
              normalizedTopicLength={normalizedTopic.length}
              maxTopicLength={MAX_TOPIC_LENGTH}
              difficulty={difficulty}
              canStartGame={canStartGame}
              canGenerate={canGenerate}
              hostAvatar={room?.host.avatarUrl}
              hostName={hostName}
              displayUserName={displayUserName}
              displayRoomCode={displayRoomCode}
              onSubmit={handleGenerate}
              onTopicChange={setTopic}
              onSelectDifficulty={setDifficulty}
            />
          ) : (
            <LobbyPlayerWaitingView
              displayUserName={displayUserName}
              displayRoomCode={displayRoomCode}
              hostName={hostName}
              hostPlayer={hostPlayer}
              youPlayer={youPlayer}
              playerSlots={playerSlots}
            />
          )}
        </div>
      </div>
      <InfoModal
        open={modalMessage !== null}
        title="Message"
        message={modalMessage ?? ""}
        onClose={() => {
          setModalMessage(null)
        }}
      />
    </div>
  )
}

export default Lobby
