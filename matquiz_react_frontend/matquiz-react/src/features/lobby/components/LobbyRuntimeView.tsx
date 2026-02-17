import LockOutlineIcon from "@mui/icons-material/LockOutline"
import { AnimatePresence, motion, useReducedMotion } from "motion/react"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import type { RankedPlayer, RuntimeStage } from "@/features/lobby/runtimeTypes"
import type { Difficulty, GamePlayerDto, QuestionDto } from "@/types/api"
import styles from "@/pages/Lobby.module.css"

const ANSWER_LABELS = ["A", "B", "C", "D"] as const

type ResultsCardSlot = {
  entry: RankedPlayer | null
  title: string
  featured?: boolean
}

type LobbyRuntimeViewProps = {
  stage: RuntimeStage
  isHost: boolean
  hostName?: string
  currentEloRating?: number
  selectedTopicLabel: string
  selectedDifficulty: Difficulty
  startCountdown: number
  skipCountdown: number
  questionProgressPercent: number
  revealProgressPercent: number
  displayedQuestion: QuestionDto | null
  selectedAnswerIndex: number | null
  lockedAnswerIndex: number | null
  isSubmittingAnswer: boolean
  revealedCorrectIndex: number | null
  rankedPlayers: RankedPlayer[]
  rankedSlots: (RankedPlayer | null)[]
  userName?: string
  yourPlacement?: RankedPlayer
  revealOutcome: "correct" | "wrong" | null
  canUseManualNextFallback: boolean
  runtimePlayers: GamePlayerDto[]
  answeredPlayerNames: string[]
  displayRoomCode: string
  yourScore: number
  onSelectAnswer: (index: number) => void
  onLockAnswer: () => void
  onManualNext: () => void
}

const LobbyRuntimeView = ({
  stage,
  isHost,
  hostName,
  currentEloRating,
  selectedTopicLabel,
  selectedDifficulty,
  startCountdown,
  skipCountdown,
  questionProgressPercent,
  revealProgressPercent,
  displayedQuestion,
  selectedAnswerIndex,
  lockedAnswerIndex,
  isSubmittingAnswer,
  revealedCorrectIndex,
  rankedPlayers,
  rankedSlots,
  userName,
  yourPlacement,
  revealOutcome,
  canUseManualNextFallback,
  runtimePlayers,
  answeredPlayerNames,
  displayRoomCode,
  yourScore,
  onSelectAnswer,
  onLockAnswer,
  onManualNext,
}: LobbyRuntimeViewProps) => {
  const isCountdownStage = stage === "START_COUNTDOWN" || stage === "SKIP_COUNTDOWN"
  const difficultyLabel = selectedDifficulty === "ADVANCED" ? "advanced" : "easy"
  const answeredPlayersSet = new Set(answeredPlayerNames)
  const shouldReduceMotion = useReducedMotion()
  const hostResultsEntry = hostName
    ? rankedPlayers.find(entry => entry.player.nickname === hostName) ?? null
    : null
  const yourResultsEntry = userName
    ? rankedPlayers.find(entry => entry.player.nickname === userName) ?? null
    : null
  const nonHostResultsEntries = rankedPlayers.filter(
    entry => entry.player.nickname !== hostName,
  )
  const nonHostAndNonSelfEntries = rankedPlayers.filter(entry => {
    if (entry.player.nickname === hostName) {
      return false
    }
    return entry.player.nickname !== userName
  })
  const resultsLeftSlots: ResultsCardSlot[] = isHost
    ? [
        {
          entry: nonHostResultsEntries.at(0) ?? null,
          title: nonHostResultsEntries.at(0)
            ? `#${String(nonHostResultsEntries[0].rank)}`
            : "",
        },
        {
          entry: nonHostResultsEntries.at(1) ?? null,
          title: nonHostResultsEntries.at(1)
            ? `#${String(nonHostResultsEntries[1].rank)}`
            : "",
        },
        {
          entry: nonHostResultsEntries.at(2) ?? null,
          title: nonHostResultsEntries.at(2)
            ? `#${String(nonHostResultsEntries[2].rank)}`
            : "",
        },
        {
          entry: nonHostResultsEntries.at(3) ?? null,
          title: nonHostResultsEntries.at(3)
            ? `#${String(nonHostResultsEntries[3].rank)}`
            : "",
        },
      ]
    : [
        {
          entry: yourResultsEntry,
          title: yourResultsEntry ? `You got #${String(yourResultsEntry.rank)}` : "You got #-",
          featured: true,
        },
        {
          entry: nonHostAndNonSelfEntries.at(0) ?? null,
          title: nonHostAndNonSelfEntries.at(0)
            ? `#${String(nonHostAndNonSelfEntries[0].rank)}`
            : "",
        },
        {
          entry: nonHostAndNonSelfEntries.at(1) ?? null,
          title: nonHostAndNonSelfEntries.at(1)
            ? `#${String(nonHostAndNonSelfEntries[1].rank)}`
            : "",
        },
        {
          entry: nonHostAndNonSelfEntries.at(2) ?? null,
          title: nonHostAndNonSelfEntries.at(2)
            ? `#${String(nonHostAndNonSelfEntries[2].rank)}`
            : "",
        },
      ]
  const currentRatingLabel =
    typeof currentEloRating === "number" ? `${String(currentEloRating)} ELO` : "--"

  const renderRuntimeStage = () => {
    if (stage === "START_COUNTDOWN") {
      return (
        <section className={`${styles.stageCard} ${styles.startStageCard}`}>
          <h2 className={styles.startHeadline}>
            The topic is <span>{selectedTopicLabel || "Unknown"}</span>
          </h2>
          <p className={styles.startHeadline}>
            The difficulty is <span>{difficultyLabel}!</span>
          </p>
          <div className={styles.startCountdownPill}>
            <p className={styles.startCountdownValue}>
              {startCountdown > 0 ? `${String(startCountdown)}...` : "Waiting for question..."}
            </p>
          </div>
        </section>
      )
    }

    if (stage === "SKIP_COUNTDOWN") {
      return (
        <section className={`${styles.stageCard} ${styles.startStageCard}`}>
          <h2 className={styles.skipTitle}>Host skipped this question</h2>
          <p className={styles.skipSubtitle}>Next one starting in...</p>
          <div className={styles.startCountdownPill}>
            <p className={styles.startCountdownValue}>{`${String(skipCountdown)}...`}</p>
          </div>
        </section>
      )
    }

    if (stage === "QUESTION" && displayedQuestion) {
      return (
        <section className={styles.questionShell}>
          <article className={styles.questionCard}>
            <p className={styles.questionNumber}>
              Question #{String(displayedQuestion.order_index)}
            </p>
            <div className={styles.questionPrompt}>
              <p className={styles.questionText}>{displayedQuestion.question_text}</p>
            </div>
          </article>
          <aside className={styles.answersPanel}>
            {displayedQuestion.answers.map((answer, index) => {
              const isSelected = selectedAnswerIndex === index
              const isLocked = lockedAnswerIndex === index
              return (
                <button
                  key={`${String(displayedQuestion.questionId)}-${String(index)}`}
                  type="button"
                  className={`${styles.answerOption} ${
                    isSelected ? styles.answerSelected : ""
                  } ${isLocked ? styles.answerLocked : ""}`}
                  disabled={lockedAnswerIndex !== null}
                  onClick={() => {
                    onSelectAnswer(index)
                  }}
                >
                  <span className={styles.answerOptionText}>
                    {ANSWER_LABELS[index]}. {answer}
                  </span>
                  {isLocked ? <LockOutlineIcon className={styles.lockIconInline} /> : null}
                </button>
              )
            })}
            <button
              type="button"
              className={`${styles.lockAnswerButton} ${
                selectedAnswerIndex === null || lockedAnswerIndex !== null
                  ? styles.lockAnswerDisabled
                  : ""
              }`}
              disabled={
                selectedAnswerIndex === null ||
                lockedAnswerIndex !== null ||
                isSubmittingAnswer
              }
              onClick={onLockAnswer}
            >
              {lockedAnswerIndex !== null
                ? "Answer locked"
                : isSubmittingAnswer
                  ? "Locking..."
                  : "Lock answer"}
            </button>
          </aside>
        </section>
      )
    }

    if (stage === "REVEAL" && displayedQuestion) {
      const correctLabel =
        revealedCorrectIndex !== null
          ? `${ANSWER_LABELS[revealedCorrectIndex]}. ${displayedQuestion.answers[revealedCorrectIndex]}`
          : "Pending"
      const revealAccentClass =
        revealOutcome === "correct"
          ? styles.revealTopCardCorrect
          : revealOutcome === "wrong"
            ? styles.revealTopCardWrong
            : ""

      return (
        <section className={styles.revealShell}>
          <article className={`${styles.revealTopCard} ${revealAccentClass}`}>
            <h2 className={styles.revealTitle}>
              Question #{String(displayedQuestion.order_index)} - Results
            </h2>
            <div className={styles.revealPromptCard}>
              <p className={styles.revealQuestionText}>{displayedQuestion.question_text}</p>
            </div>
            <div className={styles.revealCorrectRow}>
              <p className={styles.revealCorrectLabel}>Correct Answer</p>
              <p className={styles.revealCorrectValue}>{correctLabel}</p>
            </div>
          </article>

          <article className={styles.revealLeaderboardCard}>
            <div className={styles.revealLeaderboardGrid}>
              <div className={styles.revealLeaderboardList}>
                {rankedSlots.map((entry, index) => (
                  <div
                    key={`rank-${String(index)}-${entry?.player.nickname ?? "empty"}`}
                    className={styles.revealLeaderboardRow}
                  >
                    <span
                      className={`${styles.revealRankIndex} ${
                        entry ? "" : styles.revealRankIndexEmpty
                      }`}
                    >
                      {String(index + 1)}.
                    </span>
                    <div
                      className={`${styles.revealRankPill} ${
                        entry?.player.nickname === userName ? styles.revealRankPillYou : ""
                      } ${entry ? "" : styles.revealRankPillEmpty}`}
                    >
                      {entry ? `#${entry.player.nickname}` : "No player"}
                    </div>
                  </div>
                ))}
              </div>
              <div className={styles.revealPlacement}>
                <p>Your place:</p>
                <span>{yourPlacement ? `#${String(yourPlacement.rank)}` : "-"}</span>
              </div>
            </div>
            {isHost && canUseManualNextFallback ? (
              <button
                type="button"
                className={styles.nextFallbackButton}
                onClick={onManualNext}
              >
                Request next question
              </button>
            ) : null}
          </article>
        </section>
      )
    }

    if (stage === "RESULTS") {
      const topSlots = resultsLeftSlots.slice(0, 2)
      const bottomSlots = resultsLeftSlots.slice(2, 4)
      const hostRankLabel = hostResultsEntry
        ? `Host #${String(hostResultsEntry.rank)}`
        : "Host"

      return (
        <section className={styles.resultsStage}>
          <div className={styles.resultsGrid}>
            {topSlots.map((slot, index) => (
              <article
                key={`results-top-${String(index)}-${slot.entry?.player.nickname ?? "empty"}`}
                className={`${styles.resultsPlayerCard} ${
                  slot.featured ? styles.resultsPlayerCardFeatured : ""
                } ${slot.entry ? "" : styles.resultsPlayerCardPlaceholder}`}
              >
                {slot.title ? (
                  <h2 className={styles.resultsPlayerCardTitle}>{slot.title}</h2>
                ) : null}
                <div className={styles.resultsAvatarWrap}>
                  <img
                    src={slot.entry?.player.avatarUrl ?? profilePlaceholder}
                    alt={slot.entry?.player.nickname ?? "No player"}
                    className={styles.resultsAvatar}
                  />
                </div>
                <p className={styles.resultsNickname}>
                  {slot.entry ? `#${slot.entry.player.nickname}` : "No player"}
                </p>
                {slot.entry ? (
                  <p className={styles.resultsScoreLine}>
                    <span>Score: </span>
                    <span className={styles.resultsScoreAccent}>
                      {String(slot.entry.player.score)}
                    </span>
                  </p>
                ) : null}
              </article>
            ))}

            <article className={styles.resultsRatingCard}>
              <p className={styles.resultsRatingLabel}>Current Rating</p>
              <p className={styles.resultsRatingValue}>{currentRatingLabel}</p>
            </article>

            {bottomSlots.map((slot, index) => (
              <article
                key={`results-bottom-${String(index)}-${slot.entry?.player.nickname ?? "empty"}`}
                className={`${styles.resultsPlayerCard} ${
                  slot.featured ? styles.resultsPlayerCardFeatured : ""
                } ${slot.entry ? "" : styles.resultsPlayerCardPlaceholder}`}
              >
                {slot.title ? (
                  <h2 className={styles.resultsPlayerCardTitle}>{slot.title}</h2>
                ) : null}
                <div className={styles.resultsAvatarWrap}>
                  <img
                    src={slot.entry?.player.avatarUrl ?? profilePlaceholder}
                    alt={slot.entry?.player.nickname ?? "No player"}
                    className={styles.resultsAvatar}
                  />
                </div>
                <p className={styles.resultsNickname}>
                  {slot.entry ? `#${slot.entry.player.nickname}` : "No player"}
                </p>
                {slot.entry ? (
                  <p className={styles.resultsScoreLine}>
                    <span>Score: </span>
                    <span className={styles.resultsScoreAccent}>
                      {String(slot.entry.player.score)}
                    </span>
                  </p>
                ) : null}
              </article>
            ))}

            <article
              className={`${styles.resultsPlayerCard} ${
                hostResultsEntry ? "" : styles.resultsPlayerCardPlaceholder
              }`}
            >
              <h2 className={styles.resultsPlayerCardTitle}>{hostRankLabel}</h2>
              <div className={styles.resultsAvatarWrap}>
                <img
                  src={hostResultsEntry?.player.avatarUrl ?? profilePlaceholder}
                  alt={hostResultsEntry?.player.nickname ?? "No player"}
                  className={styles.resultsAvatar}
                />
              </div>
              <p className={styles.resultsNickname}>
                {hostResultsEntry ? `#${hostResultsEntry.player.nickname}` : "No player"}
              </p>
              <p className={styles.resultsScoreLine}>
                <span>Score: </span>
                <span className={styles.resultsScoreAccent}>
                  {String(hostResultsEntry?.player.score ?? 0)}
                </span>
              </p>
            </article>
          </div>
        </section>
      )
    }

    return (
      <section className={styles.stageCard}>
        <h2 className={styles.stageTitle}>Preparing game state...</h2>
      </section>
    )
  }

  return (
    <div
      className={`${styles.runtimeLayout} ${
        isCountdownStage ? styles.runtimeLayoutCountdown : ""
      } ${stage !== "RESULTS" ? styles.runtimeLayoutWithOverlay : ""}
      }`}
    >
      <div
        className={`${styles.runtimeMain} ${
          isCountdownStage ? styles.runtimeMainCountdown : ""
        } ${
          stage === "QUESTION" ? styles.runtimeMainQuestion : ""
        } ${stage === "REVEAL" ? styles.runtimeMainReveal : ""} ${
          stage === "RESULTS" ? styles.runtimeMainResults : ""
        }`}
      >
        {stage === "QUESTION" || stage === "REVEAL" ? (
          <div
            className={`${styles.questionProgress} ${
              stage === "QUESTION"
                ? styles.questionProgressQuestion
                : styles.questionProgressReveal
            }`}
          >
            <div
              className={styles.questionProgressFill}
              style={{
                width: `${String(
                  stage === "QUESTION"
                    ? questionProgressPercent
                    : revealProgressPercent,
                )}%`,
              }}
            />
          </div>
        ) : null}
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={`${stage}-${String(displayedQuestion?.questionId ?? "no-question")}`}
            className={styles.runtimeStageMotion}
            initial={
              shouldReduceMotion
                ? false
                : { opacity: 0, y: 12, scale: 0.995, filter: "blur(4px)" }
            }
            animate={
              shouldReduceMotion
                ? { opacity: 1 }
                : { opacity: 1, y: 0, scale: 1, filter: "blur(0px)" }
            }
            exit={
              shouldReduceMotion
                ? { opacity: 0 }
                : { opacity: 0, y: -10, scale: 0.995, filter: "blur(3px)" }
            }
            transition={
              shouldReduceMotion
                ? { duration: 0 }
                : { duration: 0.24, ease: "easeOut" }
            }
          >
            {renderRuntimeStage()}
          </motion.div>
        </AnimatePresence>
      </div>
      {stage !== "RESULTS" ? (
        <aside className={styles.runtimeOverlay}>
          <div className={styles.sidebarPlayers}>
            {runtimePlayers.map((player, index) => (
              <div
                key={player.nickname}
                className={`${styles.sidebarAvatarWrap} ${
                  answeredPlayersSet.has(player.nickname) ? styles.sidebarAvatarAnswered : ""
                }`}
                style={{ zIndex: runtimePlayers.length - index }}
              >
                <img
                  src={player.avatarUrl ?? profilePlaceholder}
                  alt={player.nickname}
                  className={styles.sidebarAvatar}
                />
              </div>
            ))}
          </div>
          <div className={styles.sidebarScore}>
            <p className={styles.sidebarScoreTitle}>Score</p>
            <strong className={styles.sidebarScoreValue}>{String(yourScore)}</strong>
            <p className={styles.sidebarRoomLabel}>Room:</p>
            <strong className={styles.sidebarRoomValue}>#{displayRoomCode}</strong>
          </div>
        </aside>
      ) : null}
    </div>
  )
}

export default LobbyRuntimeView
