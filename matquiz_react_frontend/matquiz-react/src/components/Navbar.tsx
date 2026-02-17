import { useEffect, useRef, useState, type CSSProperties } from "react"
import { AnimatePresence, motion, useReducedMotion } from "motion/react"
import { useLocation, useNavigate } from "react-router-dom"
import { useAppDispatch, useAppSelector } from "@/app/hooks"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import {
  useEndGameMutation,
  useRequestQuestionMutation,
} from "@/features/game/api/gameApiSlice"
import LeaveGameModal from "@/features/game/components/LeaveGameModal"
import { useLeaveGameGuard } from "@/features/game/hooks/useLeaveGameGuard"
import { setLastError } from "@/features/game/slice/gameSlice"
import logo from "@/assets/logo.png"
import styles from "./Navbar.module.css"
import { ROUTES, ROUTE_LABELS, type RoutePath } from "@/routes/paths"

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

const Navbar = () => {
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuDropdownRight, setMenuDropdownRight] = useState(0)
  const [menuDropdownMaxHeight, setMenuDropdownMaxHeight] = useState(() =>
    typeof window === "undefined" ? 720 : window.innerHeight - 24,
  )
  const navRef = useRef<HTMLElement | null>(null)
  const menuRef = useRef<HTMLDivElement | null>(null)
  const dropdownRef = useRef<HTMLDivElement | null>(null)
  const shouldReduceMotion = useReducedMotion()
  const location = useLocation()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const token = useAppSelector(state => state.auth.token)
  const { data: user } = useGetSessionQuery(undefined, { skip: !token })
  const activeRoomSnapshot = useAppSelector(state => state.game.activeRoomSnapshot)
  const activeRoomCode = useAppSelector(state => state.game.activeRoomCode)
  const currentQuestion = useAppSelector(state => state.game.currentQuestion)
  const correctAnswer = useAppSelector(state => state.game.correctAnswer)
  const lobbyHeader = useAppSelector(state => state.game.lobbyHeader)
  const [requestQuestion, { isLoading: isSkippingQuestion }] =
    useRequestQuestionMutation()
  const [endGame, { isLoading: isEndingGame }] = useEndGameMutation()
  const {
    isLeaveModalOpen,
    isLeaving,
    leaveError,
    requestExit,
    closeLeaveModal,
    confirmLeave,
  } = useLeaveGameGuard()
  const label =
    lobbyHeader ?? ROUTE_LABELS[location.pathname as RoutePath] ?? null
  const labelToneClass =
    label?.startsWith("Correct answer")
      ? styles.accountLabelSuccess
      : label?.startsWith("Wrong answer")
        ? styles.accountLabelDanger
        : ""
  const roomStatus = activeRoomSnapshot?.status
  const roomCode = activeRoomSnapshot?.roomCode ?? activeRoomCode
  const isLobbyRoute = location.pathname === ROUTES.lobby
  const isGameRuntime =
    isLobbyRoute && (roomStatus === "PLAYING" || roomStatus === "FINISHED")
  const isHost = Boolean(
    user?.username &&
      activeRoomSnapshot?.host.username &&
      user.username === activeRoomSnapshot.host.username,
  )
  const hasCurrentQuestion = Boolean(currentQuestion)
  const isCurrentQuestionRevealed = Boolean(
    currentQuestion && correctAnswer?.questionId === currentQuestion.questionId,
  )
  const canSkipQuestion =
    isHost &&
    roomStatus === "PLAYING" &&
    Boolean(roomCode) &&
    hasCurrentQuestion &&
    !isCurrentQuestionRevealed
  const canEndGame = isHost && roomStatus === "PLAYING" && Boolean(roomCode)

  const syncDropdownAnchor = () => {
    if (!navRef.current || !menuRef.current) {
      return
    }
    const navRect = navRef.current.getBoundingClientRect()
    const menuRect = menuRef.current.getBoundingClientRect()
    setMenuDropdownRight(Math.max(0, navRect.right - menuRect.right))
    const dropdownTop = navRect.bottom + 10
    const availableHeight = window.innerHeight - dropdownTop - 12
    setMenuDropdownMaxHeight(Math.max(1, Math.floor(availableHeight)))
  }

  useEffect(() => {
    if (!menuOpen) {
      return
    }
    syncDropdownAnchor()

    const handleResize = () => {
      syncDropdownAnchor()
    }

    const handleClick = (event: MouseEvent) => {
      const target = event.target as Node
      if (menuRef.current?.contains(target) || dropdownRef.current?.contains(target)) {
        return
      }
      setMenuOpen(false)
    }

    window.addEventListener("resize", handleResize)
    document.addEventListener("mousedown", handleClick)
    return () => {
      window.removeEventListener("resize", handleResize)
      document.removeEventListener("mousedown", handleClick)
    }
  }, [menuOpen])

  useEffect(() => {
    setMenuOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (isGameRuntime) {
      setMenuOpen(false)
    }
  }, [isGameRuntime])

  const performLogout = () => {
    setMenuOpen(false)
    void navigate(ROUTES.logout)
  }

  const handleLogout = () => {
    setMenuOpen(false)
    requestExit(performLogout)
  }

  const menuItems = [
    { label: "Join a game", accent: true, to: ROUTES.gameControl },
    { label: "Host a game", to: ROUTES.gameControl },
    { label: "My profile", to: ROUTES.profile },
    { label: "Leaderboard", to: ROUTES.leaderboard },
    { label: "Settings", to: ROUTES.settings },
    { label: "Help", to: ROUTES.help },
  ]

  const handleNavigate = (to: string) => {
    setMenuOpen(false)
    if (to === location.pathname) {
      return
    }
    requestExit(() => {
      void navigate(to)
    })
  }

  const handleRuntimeExit = () => {
    requestExit(() => {
      void navigate(ROUTES.gameControl)
    })
  }

  const handleSkipQuestion = () => {
    if (!canSkipQuestion || !roomCode) {
      return
    }
    void requestQuestion({ roomCode })
      .unwrap()
      .catch((error: unknown) => {
        dispatch(
          setLastError(
            parseSocketError(error, "Could not skip this question. Please retry."),
          ),
        )
      })
  }

  const handleEndGame = () => {
    if (!canEndGame || !roomCode) {
      return
    }
    void endGame({ roomCode })
      .unwrap()
      .catch((error: unknown) => {
        dispatch(
          setLastError(parseSocketError(error, "Could not end game. Please retry.")),
        )
      })
  }

  return (
    <>
      <motion.nav
        ref={navRef}
        className={styles.navbar}
        style={
          {
            "--menu-dropdown-right": `${String(menuDropdownRight)}px`,
            "--menu-dropdown-max-height": `${String(menuDropdownMaxHeight)}px`,
          } as CSSProperties
        }
        initial={shouldReduceMotion ? false : { opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={
          shouldReduceMotion
            ? { duration: 0 }
            : { duration: 0.28, ease: "easeOut" }
        }
      >
        <div className={styles.inner}>
          <button
            type="button"
            className={styles.logoButton}
            onClick={() => {
              handleNavigate(ROUTES.home)
            }}
            aria-label="Go to home"
          >
            <span className={styles.logo}>
              <img src={logo} alt="MatQuiz" />
            </span>
          </button>
          {label ? (
            <span className={`${styles.accountLabel} ${labelToneClass}`}>{label}</span>
          ) : (
            <span className={styles.spacer} />
          )}
          <div className={styles.actions}>
            {isGameRuntime ? (
              <div className={styles.gameActions}>
                {canSkipQuestion ? (
                  <button
                    type="button"
                    className={`${styles.gameActionButton} ${styles.gameActionSkipButton}`}
                    disabled={isSkippingQuestion}
                    onClick={handleSkipQuestion}
                  >
                    {isSkippingQuestion ? "Skipping..." : "Skip question"}
                  </button>
                ) : null}
                {canEndGame ? (
                  <button
                    type="button"
                    className={`${styles.gameActionButton} ${styles.gameActionEndButton}`}
                    disabled={isEndingGame}
                    onClick={handleEndGame}
                  >
                    {isEndingGame ? "Ending..." : "End game"}
                  </button>
                ) : null}
                <button
                  type="button"
                  className={`${styles.gameActionButton} ${styles.gameActionExitButton}`}
                  onClick={handleRuntimeExit}
                >
                  Exit
                </button>
              </div>
            ) : (
              <>
                {token ? null : (
                  <button
                    type="button"
                    className={styles.login}
                    onClick={() => {
                      handleNavigate(ROUTES.login)
                    }}
                  >
                    Login
                  </button>
                )}
                <div ref={menuRef}>
                  <motion.button
                    className={styles.menuButton}
                    type="button"
                    aria-label="Open menu"
                    aria-expanded={menuOpen}
                    whileTap={shouldReduceMotion ? undefined : { scale: 0.95 }}
                    animate={
                      shouldReduceMotion
                        ? { scale: 1 }
                        : { scale: menuOpen ? 0.96 : 1 }
                    }
                    transition={{ duration: shouldReduceMotion ? 0 : 0.18 }}
                    onClick={() => {
                      syncDropdownAnchor()
                      setMenuOpen(prev => !prev)
                    }}
                  >
                    <motion.span animate={menuOpen ? { rotate: 45, y: 7 } : { rotate: 0, y: 0 }} />
                    <motion.span animate={menuOpen ? { opacity: 0 } : { opacity: 1 }} />
                    <motion.span animate={menuOpen ? { rotate: -45, y: -7 } : { rotate: 0, y: 0 }} />
                  </motion.button>
                </div>
              </>
            )}
          </div>
        </div>
        <AnimatePresence>
          {menuOpen ? (
            <motion.div
              ref={dropdownRef}
              className={styles.menuDropdown}
              initial={shouldReduceMotion ? false : { opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={
                shouldReduceMotion
                  ? { duration: 0 }
                  : { duration: 0.2, ease: "easeOut" }
              }
            >
              <div className={styles.menuList}>
                {menuItems.map(item => (
                  <button
                    key={item.label}
                    type="button"
                    className={`${styles.dropdownItem} ${styles.menuItem} ${
                      item.accent ? styles.menuItemAccent : ""
                    }`}
                    onClick={() => {
                      handleNavigate(item.to)
                    }}
                  >
                    {item.label}
                  </button>
                ))}
                {token ? (
                  <button
                    type="button"
                    className={`${styles.dropdownItem} ${styles.menuItem} ${styles.menuItemLogout}`}
                    onClick={handleLogout}
                  >
                    Logout
                  </button>
                ) : null}
              </div>
              <button
                type="button"
                className={styles.menuClose}
                onClick={() => {
                  setMenuOpen(false)
                }}
              >
                X
              </button>
            </motion.div>
          ) : null}
        </AnimatePresence>
      </motion.nav>
      <LeaveGameModal
        open={isLeaveModalOpen}
        isLoading={isLeaving}
        error={leaveError}
        onCancel={closeLeaveModal}
        onConfirm={confirmLeave}
      />
    </>
  )
}

export default Navbar
