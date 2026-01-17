
# MatQuiz API Documentation

## WebSocket Connection
All game-related communication happens via **STOMP over WebSockets**.

**Connection Details:**
- **Endpoint:** `ws://localhost:8080/ws` (SockJS enabled)
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **App Prefix:** `/app`
- **Broker Prefixes:** `/topic` (public broadcasts), `/queue` (private messages)

---

## WebSocket Endpoints

### 1. Create Room
**Host creates a new game lobby.**

- **Destination:** `/app/create`
- **Payload:** `{}` (empty)
- **Private Response:** `/user/queue/created` → `GameRoomDto`
- **Behavior:**
    - Creates a new lobby with a random 5-character room code.
    - Registers the host as the first player.
    - Removes host from any previous active rooms.

---

### 2. Join Room
**Player joins an existing lobby.**

- **Destination:** `/app/join`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Private Response:** `/user/queue/joined` → `GameRoomDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Room code is normalized to uppercase.
    - Cannot join rooms with status `PLAYING` or `FINISHED`.
    - Max 5 players per room.
    - If player was previously in the room, reconnects them.
    - Removes player from any other active rooms.

---

### 3. Reconnect
**Player attempts to reconnect to an active game after disconnect/refresh.**

- **Destination:** `/app/reconnect`
- **Payload:** `{}` (empty)
- **Private Response:** `/user/queue/reconnected` → `GameRoomDto` (if active game found)
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Searches for the user's most recent active game (excludes `FINISHED`).
    - Updates session ID and sets `isConnected = true`.
    - If no active game, returns nothing.

---

### 4. Leave Room
**Player explicitly leaves a room.**

- **Destination:** `/app/leave`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Private Response:** `/user/queue/left` → `String` (room code)
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (if room still exists)
- **Behavior:**
    - Removes player from the room.
    - If the host leaves:
        - **Empty room:** Room is deleted.
        - **Players remain:** Host is transferred to the next player.
    - If last player leaves, room is deleted.

---

### 5. Generate Quiz
**Host requests AI to generate questions for the room.**

- **Destination:** `/app/generate`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12",
    "topic": "Mathematics",
    "difficulty": "EASY"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Only the host can generate the quiz.
    - Room status must be `WAITING`.
    - `difficulty` must be `EASY` or `ADVANCED`.
    - Room status changes to `GENERATING`, then to `READY` when complete.
    - 5 questions are generated and stored.

---

### 6. Start Game
**Host starts the game (transitions from READY to PLAYING).**

- **Destination:** `/app/startGame`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Room status must be `READY`.
    - Status changes to `PLAYING`.
    - Host can now request questions.

---

### 7. Request Question
**Host requests the next question to be sent to all players.**

- **Destination:** `/app/requestQuestion`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12"
  }
  ```
- **Public Broadcast:** `/topic/room/{roomCode}` → `QuestionDto`
- **Behavior:**
    - Only the host can request questions.
    - Room status must be `PLAYING`.
    - Sends the current question based on `currentQuestionIndex`.
    - Increments the question index.
    - When all questions are answered, sets status to `FINISHED` and broadcasts final `GameRoomDto`.
    - Question's `postedAt` timestamp is recorded for anti-cheat validation.

---

### 8. Submit Answer
**Player submits their answer to the current question.**

- **Destination:** `/app/submitAnswer`
- **Payload:**
  ```json
  {
    "roomCode": "ABC12",
    "questionId": 123,
    "selectedAnswerIndex": 2,
    "submissionTime": "2026-01-17T14:30:00Z"
  }
  ```
- **Private Response:** `/user/queue/answerResult` → `AnswerResultDto`
- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto` (updated scores)
- **Behavior:**
    - Room status must be `PLAYING`.
    - Player can only answer each question once.
    - Answer index must be valid (0-3).
    - Points awarded: 100 for correct, 0 for incorrect.
    - Player score is updated immediately.
    - Time taken is calculated from `question.postedAt` to `submissionTime`.

---

### 9. Automatic Events (Server-Initiated)

#### Disconnect Event
**When a player's WebSocket connection closes.**

- **Public Broadcast:** `/topic/room/{roomCode}` → `GameRoomDto`
- **Behavior:**
    - Player's `isConnected` status is set to `false`.
    - Room state is broadcast to all remaining players.
    - Player data is retained (they can reconnect).

#### Error Messages
**When an action fails.**

- **Private Response:** `/user/queue/errors` → `String` (error message)
- **Examples:**
    - "Room not found"
    - "Only the host can generate the quiz!"
    - "You have already answered this question!"

---

## REST Authentication
**Base URL:** `/api/auth`

### 1. Register
- **Endpoint:** `POST /api/auth/register`
- **Payload:**
  ```json
  {
    "username": "matei",
    "password": "securePass123",
    "email": "matei@example.com",
    "avatarUrl": "https://..."
  }
  ```
- **Response:** `Boolean` (true if successful)

---

### 2. Login
- **Endpoint:** `POST /api/auth/login`
- **Payload:**
  ```json
  {
    "username": "matei",
    "password": "securePass123"
  }
  ```
- **Response:** `String` (JWT Token)

---

### 3. Get Session
- **Endpoint:** `GET /api/auth/session`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response:**
  ```json
  {
    "id": 1,
    "username": "matei",
    "email": "matei@example.com",
    "role": "USER",
    "avatarUrl": "https://..."
  }
  ```

---

## Data Structures (DTOs)

### `GameRoomDto`
**Represents the full state of a game room.**

```json
{
  "roomCode": "ABC12",
  "topic": "Mathematics",
  "difficulty": "EASY",
  "status": "PLAYING",
  "host": {
    "username": "matei",
    "avatarUrl": "https://..."
  },
  "players": [
    {
      "nickname": "matei",
      "score": 100,
      "isConnected": true,
      "avatarUrl": "https://..."
    },
    {
      "nickname": "john",
      "score": 50,
      "isConnected": false,
      "avatarUrl": "https://..."
    }
  ]
}
```

**Status Values:**
- `WAITING` – Room created, waiting for quiz generation
- `GENERATING` – AI is generating questions
- `READY` – Quiz ready, waiting for host to start
- `PLAYING` – Game in progress
- `FINISHED` – All questions answered

---

### `QuestionDto`
**Represents a single quiz question (without the correct answer).**

```json
{
  "questionId": 42,
  "question_text": "What is 2 + 2?",
  "answers": ["3", "4", "5", "6"],
  "order_index": 1
}
```

**Notes:**
- `correctIndex` is **not included** to prevent cheating.
- `answers` is a 4-element array of strings.
- `order_index` indicates the question number in the quiz.

---

### `AnswerResultDto`
**Sent to a player after they submit an answer.**

```json
{
  "questionId": 42,
  "isCorrect": true,
  "correctAnswerIndex": 1,
  "pointsEarned": 100,
  "newTotalScore": 250
}
```

---

### `GamePlayerDto`
**Represents a player in the room.**

```json
{
  "nickname": "matei",
  "score": 150,
  "isConnected": true,
  "avatarUrl": "https://..."
}
```

---

### `UserSummaryDto`
**Minimal user info (used for host display).**

```json
{
  "username": "matei",
  "avatarUrl": "https://..."
}
```

---

## Request DTOs

### `JoinRoomRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `LeaveRoomRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `GenerateQuizRequest`
```json
{
  "roomCode": "ABC12",
  "topic": "Science",
  "difficulty": "ADVANCED"
}
```

**Difficulty Options:** `EASY`, `ADVANCED`

### `StartGameRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `QuestionRequest`
```json
{
  "roomCode": "ABC12"
}
```

### `AnswerSubmissionRequest`
```json
{
  "roomCode": "ABC12",
  "questionId": 123,
  "selectedAnswerIndex": 2,
  "submissionTime": "2026-01-17T14:30:00Z"
}
```

---

## Game Flow Example

1. **Matei** creates a room → Receives `GameRoomDto` (status: `WAITING`)
2. **John** joins via `/app/join` → Both receive updated `GameRoomDto`
3. **Matei** generates quiz → Status changes to `GENERATING`, then `READY`
4. **Matei** starts game → Status changes to `PLAYING`
5. **Matei** requests question → All players receive `QuestionDto`
6. **John** submits answer → Receives `AnswerResultDto`, all players see updated scores
7. Repeat step 5-6 for all questions
8. After last question → Status changes to `FINISHED`, final `GameRoomDto` with leaderboard

---

## Error Handling

All errors are sent to `/user/queue/errors` as plain `String` messages.

**Common Errors:**
- `"Room not found"`
- `"Only the host can generate the quiz!"`
- `"You cannot join a game that is already PLAYING"`
- `"Invalid question index: 5"`
- `"You have already answered this question!"`

---

## Notes for Frontend

- Always normalize room codes to **uppercase** before sending.
- Subscribe to `/topic/room/{roomCode}` to receive real-time updates.
- The `QuestionDto` does **not** include the correct answer—it's only revealed in `AnswerResultDto` after submission.
- Player `isConnected` status updates in real-time when users disconnect.
- Use `submissionTime` from the client to allow server-side anti-cheat validation (time-based scoring in the future).

---

**Last Updated:** 2026-01-17
```