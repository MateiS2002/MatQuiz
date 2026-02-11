import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from "react"
import { useGetSessionQuery } from "@/features/auth/api/authApiSlice"
import profilePlaceholder from "@/assets/profile-placeholder.png"
import {
  useGetLeaderboardForUserMutation,
  useGetLeaderboardQuery,
} from "@/features/auth/api/authApiSlice"
import styles from "./Leaderboard.module.css"

const Leaderboard = () => {
  const leaderboardRef = useRef<HTMLDivElement | null>(null)
  const darkContainerRef = useRef<HTMLDivElement | null>(null)
  const { data: user } = useGetSessionQuery(undefined)
  const { data: leaderboard = [] } = useGetLeaderboardQuery(undefined)
  const [fetchUserRank, { data: userRank = [] }] =
    useGetLeaderboardForUserMutation()
  const [leaderboardAvailableHeight, setLeaderboardAvailableHeight] = useState(
    () => (typeof window === "undefined" ? 900 : window.innerHeight),
  )
  const [leaderboardContentScale, setLeaderboardContentScale] = useState(1)

  useEffect(() => {
    if (user?.username) {
      void fetchUserRank({ username: user.username })
    }
  }, [fetchUserRank, user?.username])

  const syncLeaderboardViewport = useCallback(() => {
    if (!leaderboardRef.current || !darkContainerRef.current) {
      return
    }
    const leaderboardRect = leaderboardRef.current.getBoundingClientRect()
    const availableHeight = window.innerHeight - leaderboardRect.top - 12
    const boundedHeight = Math.max(1, Math.floor(availableHeight))
    setLeaderboardAvailableHeight(boundedHeight)

    const leaderboardStyles = window.getComputedStyle(leaderboardRef.current)
    const paddingTop = Number.parseFloat(leaderboardStyles.paddingTop) || 0
    const paddingBottom = Number.parseFloat(leaderboardStyles.paddingBottom) || 0
    const paddingLeft = Number.parseFloat(leaderboardStyles.paddingLeft) || 0
    const paddingRight = Number.parseFloat(leaderboardStyles.paddingRight) || 0
    const safeHeight = Math.max(1, boundedHeight - paddingTop - paddingBottom)
    const safeWidth = Math.max(
      1,
      Math.floor(leaderboardRect.width - paddingLeft - paddingRight),
    )

    const contentHeight = Math.max(1, darkContainerRef.current.scrollHeight)
    const contentWidth = Math.max(1, darkContainerRef.current.scrollWidth)
    const nextScale = Math.min(
      1,
      safeHeight / contentHeight,
      safeWidth / contentWidth,
    )
    setLeaderboardContentScale(Math.max(0.55, nextScale))
  }, [])

  useEffect(() => {
    syncLeaderboardViewport()
    const handleResize = () => {
      syncLeaderboardViewport()
    }

    window.addEventListener("resize", handleResize)
    window.visualViewport?.addEventListener("resize", handleResize)
    return () => {
      window.removeEventListener("resize", handleResize)
      window.visualViewport?.removeEventListener("resize", handleResize)
    }
  }, [syncLeaderboardViewport])

  useEffect(() => {
    if (!leaderboardRef.current || !darkContainerRef.current) {
      return
    }

    if (typeof ResizeObserver === "undefined") {
      return
    }

    const observer = new ResizeObserver(() => {
      syncLeaderboardViewport()
    })

    observer.observe(leaderboardRef.current)
    observer.observe(darkContainerRef.current)
    return () => {
      observer.disconnect()
    }
  }, [syncLeaderboardViewport])

  const userEntry = userRank.at(0)

  return (
    <div
      ref={leaderboardRef}
      className={styles.leaderboard}
      style={
        {
          "--leaderboard-available-height": `${String(leaderboardAvailableHeight)}px`,
        } as CSSProperties
      }
    >
      <div
        ref={darkContainerRef}
        className={styles.darkContainer}
        style={
          {
            "--leaderboard-content-scale": String(leaderboardContentScale),
          } as CSSProperties
        }
      >
        <div className={styles.content}>
          <div className={styles.listCard}>
            <div className={styles.scrollArea}>
              <div className={`${styles.row} ${styles.rowHighlight}`}>
                <span className={styles.rowRank}>
                  #{userEntry?.rank ?? "--"}
                </span>
                <span className={styles.rowName}>
                  (You) #{user?.username ?? "John23"}
                </span>
                <span className={styles.rowElo}>
                  {userEntry?.eloRating ?? user?.eloRating ?? 1450} ELO
                </span>
              </div>
              {leaderboard.map(entry => (
                <div key={entry.rank} className={styles.row}>
                  <span className={styles.rowRank}>#{entry.rank}</span>
                  <span className={styles.rowName}>#{entry.username}</span>
                  <span className={styles.rowElo}>{entry.eloRating} ELO</span>
                </div>
              ))}
            </div>
          </div>
          <div className={styles.sidebar}>
            <div className={styles.userCard}>
              <p className={styles.cardTitle}>You</p>
              <div className={styles.avatar}>
                <img
                  src={user?.avatarUrl ?? profilePlaceholder}
                  alt={user?.username ?? "Profile avatar"}
                  className={styles.avatarImage}
                />
              </div>
              <p className={styles.cardSubtitle}>#{user?.username ?? "John23"}</p>
            </div>
            <div className={styles.statsCard}>
              <p className={styles.cardTitle}>Current</p>
              <p className={styles.cardTitle}>Rating</p>
              <p className={styles.cardSubtitle}>
                {user?.eloRating ?? 1450} ELO
              </p>
              <p className={styles.cardTitle}>Last game</p>
              <p className={styles.cardAccent}>+{user?.lastGamePoints ?? NaN}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Leaderboard
