import { useMemo, useRef, useState } from "react"
import styles from "./Help.module.css"

type HelpSlide = {
  image: string
  title: string
  description: string
}

const HELP_SLIDES: HelpSlide[] = [
{
    image: "/onboarding/homescreen-how-to-login.png",
    title: "Home Navigation",
    description:
        "The home screen guides you to account actions and core game entry points.",
},
  {
    image: "/onboarding/dropdown-menu-main-app-navigation.png",
    title: "Main Menu",
    description:
        "Use the dropdown navigation to move quickly between key sections.",
  },
  {
    image: "/onboarding/login-screen.png",
    title: "Sign In",
    description:
      "Use the login screen to access your account and load your progress. If you are new, you can press register!",
  },
  {
    image: "/onboarding/register-screen-create-account.png",
    title: "Create Account",
    description:
      "New player? Register with your credentials to start competing.",
  },
  {
    image: "/onboarding/homescreen-how-to-start-a-game.png",
    title: "Start Flow",
    description:
      "From home, continue into game control where you can host or join sessions.",
  },
  {
    image: "/onboarding/gamecontrol-page-select-join-or-create.png",
    title: "Join Or Host",
    description:
      "Pick whether you want to join an existing room or create one yourself.",
  },
  {
    image: "/onboarding/join-page-add-a-valid-roomcode.png",
    title: "Join A Room",
    description:
      "Enter a valid room code to connect to the host and join the match.",
  },
  {
    image: "/onboarding/host-gameroom-input-topic-to-generate-quiz.png",
    title: "Choose Topic",
    description:
      "Hosts enter a topic to generate a quiz tailored to the session.",
  },
  {
    image: "/onboarding/host-gameroom-wait-quiz-generation.png",
    title: "Quiz Generation",
    description:
      "Wait while MatQuiz prepares your questions before the game begins.",
  },
  {
    image: "/onboarding/host-gameroom-quiz-ready.png",
    title: "Room Ready",
    description:
      "When the quiz is ready, confirm players and launch the game round.",
  },
  {
    image: "/onboarding/game-topic-annoucement.png",
    title: "Topic Announcement",
    description:
      "Each round starts with a clear topic announcement for all players. You have 5 seconds so pay attention!",
  },
  {
    image: "/onboarding/game-read-question-click-answer.png",
    title: "Answer Questions",
    description:
      "Read the question, then tap the answer you believe is correct.",
  },
  {
    image: "/onboarding/game-press-to-lock-answer.png",
    title: "Lock In",
    description:
      "Press lock to confirm your answer and avoid accidental changes and get more points for early answers." +
        " Scoring is TIME BASED!",
  },
  {
    image: "/onboarding/game-answer-locker-wait-for-others-to-answer.png",
    title: "Wait For Players",
    description:
      "After locking your response, wait while other players submit theirs. Or the 30 seconds timer runs out.",
  },
  {
    image: "/onboarding/game-check-answer-result-on-navbar-correct-example.png",
    title: "Correct Result",
    description:
      "The navbar feedback shows when your submitted answer is correct.",
  },
  {
    image: "/onboarding/game-check-answer-result-on-navbar-wrong-example.png",
    title: "Wrong Result",
    description:
      "If the answer is wrong, feedback appears immediately so you can learn. The correct answer is under the question!",
  },
  {
    image: "/onboarding/game-check-final-scoreboard.png",
    title: "Final Scoreboard",
    description:
      "Review the final rankings and see who performed best in the match.",
  },
  {
    image: "/onboarding/leaderboard-page-global.png",
    title: "Global Leaderboard",
    description:
      "Track your long-term progress and compare your ELO against everyone.",
  },

  {
    image: "/onboarding/profile-page-manage.png",
    title: "Manage Profile",
    description:
      "Update account details and security settings from your profile page.",
  },
  {
    image: "/onboarding/profile-change-picture-from-selection.png",
    title: "Choose Avatar",
    description:
      "Choose a profile picture from the available avatar collection.",
  },
]

const Help = () => {
  const scrollerRef = useRef<HTMLDivElement | null>(null)
  const [activeIndex, setActiveIndex] = useState(0)

  const totalSlides = HELP_SLIDES.length

  const handleScroll = () => {
    const scroller = scrollerRef.current
    if (!scroller) {
      return
    }

    const viewportWidth = scroller.clientWidth
    if (viewportWidth === 0) {
      return
    }

    const nextIndex = Math.round(scroller.scrollLeft / viewportWidth)
    const bounded = Math.max(0, Math.min(totalSlides - 1, nextIndex))
    setActiveIndex(bounded)
  }

  const progressText = useMemo(
    () => `${String(activeIndex + 1)} / ${String(totalSlides)}`,
    [activeIndex, totalSlides],
  )

  const goToSlide = (index: number) => {
    const scroller = scrollerRef.current
    if (!scroller) {
      return
    }

    const boundedIndex = Math.max(0, Math.min(totalSlides - 1, index))
    const targetSlide = scroller.children.item(boundedIndex)
    if (!targetSlide) {
      return
    }

    targetSlide.scrollIntoView({
      behavior: "smooth",
      inline: "start",
      block: "nearest",
    })
    setActiveIndex(boundedIndex)
  }

  return (
    <main className={styles.help}>
      <section className={styles.header}>
        <h1 className={styles.title}>Game Tutorial</h1>
        <p className={styles.subtitle}>
          Swipe through each step to learn the full MatQuiz game flow.
        </p>
      </section>

      <div
        ref={scrollerRef}
        className={styles.scroller}
        onScroll={handleScroll}
        aria-label="MatQuiz tutorial slides"
      >
        {HELP_SLIDES.map((slide, index) => (
          <article key={slide.image} className={styles.card}>
            <div className={styles.imageFrame}>
              <img
                src={slide.image}
                alt={slide.title}
                className={styles.image}
                loading={index === 0 ? "eager" : "lazy"}
                fetchPriority={index === 0 ? "high" : "auto"}
                decoding="async"
              />
            </div>
            <div className={styles.copy}>
              <h2 className={styles.cardTitle}>{slide.title}</h2>
              <p className={styles.cardDescription}>{slide.description}</p>
            </div>
          </article>
        ))}
      </div>

      <div className={styles.progress}>
        <span className={styles.progressText}>{progressText}</span>
        <div className={styles.desktopControls}>
          <button
            type="button"
            className={styles.controlButton}
            onClick={() => {
              goToSlide(activeIndex - 1)
            }}
            disabled={activeIndex === 0}
            aria-label="Go to previous tutorial card"
          >
            Prev
          </button>
          <button
            type="button"
            className={styles.controlButton}
            onClick={() => {
              goToSlide(activeIndex + 1)
            }}
            disabled={activeIndex === totalSlides - 1}
            aria-label="Go to next tutorial card"
          >
            Next
          </button>
        </div>
        <div className={styles.dots} aria-hidden>
          {HELP_SLIDES.map((slide, index) => (
            <span
              key={slide.image}
              className={`${styles.dot} ${index === activeIndex ? styles.dotActive : ""}`}
            />
          ))}
        </div>
      </div>
    </main>
  )
}

export default Help
