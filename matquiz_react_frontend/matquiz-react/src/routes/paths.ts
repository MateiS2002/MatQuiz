export const ROUTES = {
  home: "/",
  login: "/login",
  register: "/register",
  logout: "/logout",
  profile: "/profile",
  gameControl: "/game-control",
  join: "/join",
  create: "/create",
  lobby: "/lobby",
  terms: "/terms",
  leaderboard: "/leaderboard",
  settings: "/settings",
  help: "/help",
  contact: "/contact",
} as const

export type RoutePath = (typeof ROUTES)[keyof typeof ROUTES]

export const ROUTE_LABELS: Partial<Record<RoutePath, string>> = {
  [ROUTES.login]: "Account",
  [ROUTES.register]: "Account",
  [ROUTES.terms]: "Terms and Conditions",
  [ROUTES.profile]: "My profile",
  [ROUTES.gameControl]: "Game Control",
  [ROUTES.join]: "Join a room",
  [ROUTES.lobby]: "Lobby",
  [ROUTES.settings]: "Settings",
  [ROUTES.contact]: "Contact / Bug-report",
  [ROUTES.leaderboard]: "Leaderboard",
}
