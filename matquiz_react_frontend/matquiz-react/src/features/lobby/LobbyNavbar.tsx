/**
 * Lobby-specific navbar wrapper that accepts dynamic label state.
 * Styling will be applied after matching the Figma layout 1:1.
 */

import type { ReactElement } from "react"
import { Link } from "react-router-dom"
import { ROUTES } from "@/routes/paths"
import type { LobbyAction, LobbyTone } from "@/features/lobby/lobbyLabel"

type LobbyNavbarProps = {
  label: string
  tone: LobbyTone
  actions: LobbyAction[]
  onExit?: () => void
  onSkip?: () => void
  onEnd?: () => void
  logo: ReactElement
}

const LobbyNavbar = ({
  label,
  tone,
  actions,
  onExit,
  onSkip,
  onEnd,
  logo,
}: LobbyNavbarProps) => (
  <nav aria-label="Lobby navigation">
    <div>
      <Link to={ROUTES.home}>{logo}</Link>
    </div>
    <div data-tone={tone}>{label}</div>
    <div>
      {actions.includes("skip") ? (
        <button type="button" onClick={onSkip}>
          Skip question
        </button>
      ) : null}
      {actions.includes("end") ? (
        <button type="button" onClick={onEnd}>
          End game
        </button>
      ) : null}
      {actions.includes("exit") ? (
        <button type="button" onClick={onExit}>
          Exit
        </button>
      ) : null}
    </div>
  </nav>
)

export default LobbyNavbar
