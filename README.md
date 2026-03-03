# MatQuiz

> Full-stack real-time quiz platform with async AI question generation.

**Live Project:** [matquiz.mateistanescu.ro](https://matquiz.mateistanescu.ro)  
**Personal Website:** [mateistanescu.ro](https://mateistanescu.ro)  
**Contact:** [stanescumatei@protonmail.com](mailto:stanescumatei@protonmail.com)

**CV Snapshot**
- Role: Full-stack engineer (architecture, backend, frontend, deployment).
- Focus: Scalable event-driven design with Spring Boot, RabbitMQ, React, PostgreSQL.
- Status: Production-deployed portfolio project.

## Description

Online quiz game inspired by Kahoot, where questions are generated automatically by an LLM.

## Features

  * User-friendly interface
  * Account management (CRUD)
  * Real-time feedback
  * Leaderboard tracking
  * Customizable quiz settings with topic and difficulty level selection
  * Multiplayer mode with real-time synchronization
  * Simple anticheat with time validation on server-side


## Architecture and Design

MatQuiz is built as an **event-driven, service-oriented platform** deployed on a VPS with Docker Compose.  
The architecture keeps real-time gameplay in the main backend while offloading AI quiz generation to a dedicated worker service, so lobby/game interactions stay responsive.

### Key Architectural Decisions

  * **Asynchronous AI generation over RabbitMQ:** The backend publishes generation jobs and immediately returns control to the game flow (`WAITING -> GENERATING`) instead of blocking on external AI latency.
  * **Single AI worker service:** A dedicated Java AI service consumes generation jobs, calls **OpenAI**, validates output, then publishes success/failure results back to the backend.
  * **Request/Reply queue topology:** The system uses two RabbitMQ paths:
    * `quiz_generation_exchange` -> `quiz_generation_queue` (backend -> AI worker)
    * `quiz_results_exchange` -> `quiz_results_queue` (AI worker -> backend)
  * **Real-time communication:** **WebSockets** (STOMP) broadcast room state, questions, progress, and results to connected players.

### The Content Generation Flow

1. **Backend** publishes `QuizGenerationPayload` (`roomCode`, `topic`, `difficulty`) to `quiz_generation_exchange`.
2. **AI service** consumes from `quiz_generation_queue` and calls **OpenAI** (`gpt-5-mini`).
3. **AI service** enforces structured output (`JSON_SCHEMA`) and validates quiz rules (5 questions, 4 answers each, valid `correctIndex`).
4. **AI service** publishes a `SUCCESS` or `FAILED` result to `quiz_results_exchange`.
5. **Backend** consumes from `quiz_results_queue`:
   * `SUCCESS`: persists questions, moves room to `READY`, broadcasts room update.
   * `FAILED`: resets room to `WAITING`, broadcasts update, and notifies host.


## Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Backend Core** | **Spring Boot (Java)** | Manages game state, user sessions, security, and WebSocket. |
| **Frontend** | **React** | Single Page Application (SPA) for dynamic player and host interfaces. |
| **Database** | **PostgreSQL** | Persistent storage for users, game metadata, and results. Uses **JSONB** for efficient answer storage. |
| **Messaging** | **RabbitMQ** | Decouples the core application from the external AI services. |
| **AI Content** | **OpenAI (gpt-5-mini)** | Generates quiz content with schema-constrained JSON responses. |
| **Deployment** | **Docker Compose / Coolify** | Containerized deployment with reverse-proxied frontend + backend domains. |


## Stability and Reliability

The project incorporates patterns to handle the instability of external services:

  * **Queue-based decoupling:** RabbitMQ isolates gameplay from AI generation latency and temporary provider issues.
  * **Structured output + server validation:** The AI service requests JSON schema output and validates invariants before publishing results.
  * **Graceful failure path:** Failed AI generations publish explicit `FAILED` results, allowing the backend to reset room state and notify the host.
  * **Generation timeout handling:** Rooms stuck in `GENERATING` are reset to `WAITING` and hosts receive timeout notifications.
  * **Server-Side Time Validation:** Anti-cheat logic is enforced by tracking the question start time and answer submission time strictly on the server, eliminating client-side cheating.


##  Project Structure

```text
/matquiz
├── /matquiz-spring-boot-backend           # Core API, WebSocket, game state, auth
├── /matquiz-ai-service-java               # Async AI generation worker (OpenAI + RabbitMQ)
├── /matquiz_react_frontend/matquiz-react  # React SPA
├── /docker-compose.prod.yaml              # Production stack (frontend, backend, ai-service, db, broker)
└── /.env.prod.example                     # Production environment template
```

## Excalidraw Diagrams Backend

[Excalidraw File Link](https://excalidraw.com/#json=e323lmQXd7zlJJtqi9DpT,8Y8shsL2KvpSI3_dpJfklw)

<img width="3175" height="1108" alt="Excalidraw_Backend_Diagram" src="https://github.com/user-attachments/assets/604d7da1-f576-4e88-bab2-5603366d7590" />


## Figma Frontend Design

<img width="10976" height="6496" alt="FigmaDesign" src="https://github.com/user-attachments/assets/30c275e1-facf-42cb-8707-036eece193a9" />

## DB Diagrams (Main Database Schema)

[DB Diagram Link Main Database](https://dbdiagram.io/d/Mat_Quiz_Diagram-691e128c228c5bbc1a9b6451)

<img width="2105" height="1222" alt="DBDiagramMainDatabase" src="https://github.com/user-attachments/assets/b514c832-108c-4102-b14c-ea60e1239eff" />

```java
Project MatQuiz {
  database_type: 'PostgreSQL'
  Note: 'Backend database for MatQuiz (Spring Boot + RabbitMQ architecture)'
}

// 1. Users: Standard auth and profile stats
Table users {
  id bigserial [pk]

  username varchar(50) [unique, not null]
  email varchar(100) [unique, not null]
  password_hash varchar(255) [not null]
  avatar_url varchar(255) [note: 'Will provide to the users 10 default avatars that will be randomly selected']
  
  // Game Stats
  elo_rating int [default: 1000, note: 'Starts at 1000']
  total_games_played int [default: 0]
  last_game_points int [default: 0]
  
  created_at timestamp [default: `now()`]
  updated_at timestamp [default: `now()`]
}

// 2. Game Rooms: The session state
Table game_rooms {
  id bigserial [pk]
  room_code varchar(5) [unique, not null, note: 'The 5-digit PIN (ex. 56278)']
  host_id bigint [not null, ref: > users.id]
  
  // Configuration
  topic varchar(100) [not null]
  difficulty varchar(20) [not null, note: 'EASY, ADVANCED']
  question_count int [default: 5]
  
  // AI & Async Logic
  correlation_id uuid [unique, note: 'Links this room to the RabbitMQ job']
  status varchar(20) [default: 'WAITING', note: 'WAITING -> GENERATING -> READY -> PLAYING -> FINISHED']
  
  // Game Progress
  current_question_index int [default: 0]
  
  created_at timestamp [default: `now()`]
}

// 3. Questions: Generated by AI
Table questions {
  id bigserial [pk]
  
  game_room_id bigint [ref: > game_rooms.id]
  
  question_text text [not null]
  
  // Storing answers as JSONB is more efficient than a separate table for read-heavy quizzes
  // Format: ["Answer A", "Answer B", "Answer C", "Answer D"]
  answers jsonb [not null] 
  
  correct_index int [not null, note: '0-3']

  time_limit int [default: 30]
  
  order_index int [not null, note: '1, 2, 3... To ensure order']

  posted_at timestamp [note: 'Server time when question was revealed. For basic anticheat?']
}

// 4. Game Players: Who is in a specific room
Table game_players {
  id bigserial [pk]
  game_room_id bigint [ref: > game_rooms.id]
  user_id bigint [ref: > users.id]
  
  nickname varchar(50) [not null, note: 'What will be displayed on the front-end']
  socket_session_id varchar(100) [note: 'To map WebSocket messages to players']
  
  score int [default: 0]
  
  is_connected boolean [default: true, note: 'In case websocket connection drops']

  joined_at timestamp [default: `now()`, note: 'For logging']

  indexes {
    (game_room_id, user_id) [unique] // A user can only join a room once
  }
}

// 5. Player Answers:
Table player_answers {
  id bigserial [pk]
  game_player_id bigint [ref: > game_players.id]
  question_id bigint [ref: > questions.id]
  
  selected_index int
  is_correct boolean

  //Will need a network buffer TBD
  time_taken_ms int [note:'Calculated on the backend']
  points_awarded int [default: 0, note:'Points calculated based on speed on the backend']
  
  answered_at timestamp [default: `now()`]
}
```

## DB Diagrams (Backup Database Schema)

> Note: kept for historical design reference. The current production flow uses the dedicated OpenAI worker service and does not depend on this backup table.

[DB Diagram Link Backup Database](https://dbdiagram.io/d/MatQuiz-AI-Backup-Database-6924341e228c5bbc1a37a76a)

<img width="812" height="509" alt="DBDiagramBackupDatabase" src="https://github.com/user-attachments/assets/5aa7bc41-2acc-430e-a42d-57d3daa96c39" />

```java
Project MatQuiz_Backup {
  database_type: 'PostgreSQL'
  Note: 'Historical backup-cache concept for generated quizzes'
  Note: 'Not part of the current production OpenAI flow'
}

Table backup_quizzes {
  id bigserial [pk]
  
  // Metadata for matching
  topic varchar(100) [not null, note: 'Lowercase, ex "eurovision"']
  difficulty varchar(20) [not null]
  
  // The Payload
  // Stores a raw provider-like JSON payload for fallback scenarios.
  // In current architecture this table is legacy/reference only.
  raw_json_response text [not null]
  
  last_used_at timestamp [default: null]
  created_at timestamp [default: `now()`]
  
  indexes {
    (topic, difficulty) // Fast lookup for topic+difficulty fallback lookups
  }
}
```
