# MatQuiz API Documentation

## WebSocket Flow: Lobby
All game-related communication happens via STOMP over WebSockets.

**Connection Details:**
- **Endpoint:** `ws://localhost:8080/ws` (SockJS enabled)
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **App Prefix:** `/app`
- **Broker Prefixes:** `/topic`, `/queue`

### 1. Create Room
- **Destination:** `/app/create`
- **Payload:** `{}`
- **Success Response (Private):** `/user/queue/created` -> `GameRoomDto`
- **Behavior:** Creates a new lobby and registers the host as the first player.

### 2. Join Room
- **Destination:** `/app/join`
- **Payload:** `{"roomCode": "ABC12"}`
- **Success Response (Private):** `/user/queue/joined` -> `GameRoomDto`
- **Broadcast Update (Public):** `/topic/room/ABC12` -> `GameRoomDto`
- **Behavior:** Normalizes room code to uppercase. Broadcasts updated player list to everyone in the room.

### 3. Reconnect (Manual/Auto-Recovery)
- **Destination:** `/app/reconnect`
- **Payload:** `{}`
- **Success Response (Private):** `/user/queue/reconnected` -> `GameRoomDto`
- **Broadcast Update (Public):** `/topic/room/ABC12` -> `GameRoomDto`
- **Behavior:** Restores an active session after page refresh. Notifies other players of the return.

### 4. System Events (Server-to-Client)
- **Error Channel:** `/user/queue/errors` -> `String` (ErrorMessage)
- **Disconnect Event:** When a socket closes, a `GameRoomDto` is broadcast to `/topic/room/{code}` with the player's `isConnected` status set to `false`.

---

## REST Authentication
**Base URL:** `/api/auth`

### 1. Register
- **Endpoint:** `POST /register`
- **Payload:** `UserRegisterDto` (username, password, avatarUrl)
- **Response:** `Boolean`

### 2. Login
- **Endpoint:** `POST /login`
- **Payload:** `UserLoginDto` (username, password)
- **Response:** `String` (Plain JWT Token)

### 3. Get Session
- **Endpoint:** `GET /session`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:** `UserSummaryDto`

---

## Data Structures (DTOs)

### `GameRoomDto`
```json
{
  "roomCode": "ABC12",
  "topic": "Mathematics",
  "difficulty": "EASY",
  "host": { "username": "matei", "avatarUrl": "..." },
  "players": [
    { "nickname": "matei", "score": 0, "isConnected": true, "avatarUrl": "..." }
  ]
}
```

### `JoinRoomRequest`
```json
{
  "roomCode": "string"
}
```