import { lazy, Suspense } from "react"
import { Route, Routes } from "react-router-dom"
import Home from "@/pages/Home"
import Login from "@/pages/Login"
import Logout from "@/pages/Logout"
import Register from "@/pages/Register"
import Terms from "@/pages/Terms"
import Profile from "@/pages/Profile"
import Contact from "@/pages/Contact"
import GameControl from "@/pages/GameControl"
import Join from "@/pages/Join"
import Create from "@/pages/Create"
import { ROUTES } from "@/routes/paths"
import RequireAuth from "@/routes/RequireAuth"

const Lobby = lazy(() => import("@/pages/Lobby"))
const Settings = lazy(() => import("@/pages/Settings"))
const Leaderboard = lazy(() => import("@/pages/Leaderboard"))

const AppRoutes = () => (
  <Routes>
    <Route
      path={ROUTES.home}
      element={<Home />}
    />
    <Route path={ROUTES.login} element={<Login />} />
    <Route path={ROUTES.register} element={<Register />} />
    <Route path={ROUTES.terms} element={<Terms />} />
    <Route
      path={ROUTES.profile}
      element={
        <RequireAuth>
          <Profile />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.settings}
      element={
        <RequireAuth>
          <Suspense fallback={<div>Loading settings...</div>}>
            <Settings />
          </Suspense>
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.contact}
      element={
        <RequireAuth>
          <Contact />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.leaderboard}
      element={
        <RequireAuth>
          <Suspense fallback={<div>Loading leaderboard...</div>}>
            <Leaderboard />
          </Suspense>
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.gameControl}
      element={
        <RequireAuth>
          <GameControl />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.join}
      element={
        <RequireAuth>
          <Join />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.create}
      element={
        <RequireAuth>
          <Create />
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.lobby}
      element={
        <RequireAuth>
          <Suspense fallback={<div>Loading lobby...</div>}>
            <Lobby />
          </Suspense>
        </RequireAuth>
      }
    />
    <Route
      path={ROUTES.logout}
      element={
        <RequireAuth>
          <Logout />
        </RequireAuth>
      }
    />
  </Routes>
)

export default AppRoutes
