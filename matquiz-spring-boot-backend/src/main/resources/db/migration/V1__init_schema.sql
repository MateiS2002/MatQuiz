/**
 * V1: Initial schema for MatQuiz
 * Includes tables for users, game rooms, players, questions, and answers
 */

-- 1.
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       avatar_url VARCHAR(255),
                       elo_rating INT DEFAULT 1000,
                       total_games_played INT DEFAULT 0,
                       last_game_points INT DEFAULT 0,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2.
CREATE TABLE game_rooms (
                            id BIGSERIAL PRIMARY KEY,
                            room_code VARCHAR(5) UNIQUE NOT NULL,
                            host_id BIGINT NOT NULL REFERENCES users(id),
                            topic VARCHAR(100) NOT NULL,
                            difficulty INT DEFAULT 0 NOT NULL, -- 0: EASY, 1: ADVANCED
                            question_count INT DEFAULT 5,
                            correlation_id UUID UNIQUE,
                            status INT DEFAULT 0 NOT NULL,     -- 0: WAITING, 1: GENERATING, 2: READY, 3: PLAYING, 4: FINISHED
                            current_question_index INT DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT hc_difficulty_range CHECK (difficulty IN (0, 1)),
                            CONSTRAINT hc_status_range CHECK (status IN (0, 1, 2, 3, 4))
);

-- 3.
CREATE TABLE questions (
                           id BIGSERIAL PRIMARY KEY,
                           game_room_id BIGINT NOT NULL REFERENCES game_rooms(id) ON DELETE CASCADE,
                           question_text TEXT NOT NULL,
                           answers JSONB NOT NULL,
                           correct_index INT NOT NULL,
                           time_limit INT DEFAULT 30,
                           order_index INT NOT NULL,
                           posted_at TIMESTAMP -- Nullable, set when question goes live in frontend
);

-- 4.
CREATE TABLE game_players (
                              id BIGSERIAL PRIMARY KEY,
                              game_room_id BIGINT NOT NULL REFERENCES game_rooms(id) ON DELETE CASCADE,
                              user_id BIGINT REFERENCES users(id),
                              nickname VARCHAR(50) NOT NULL,
                              socket_session_id VARCHAR(100),
                              score INT DEFAULT 0,
                              is_connected BOOLEAN DEFAULT TRUE,
                              joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE(game_room_id, user_id)
);

-- 5.
CREATE TABLE player_answers (
                                id BIGSERIAL PRIMARY KEY,
                                game_player_id BIGINT NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
                                question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
                                selected_index INT,
                                is_correct BOOLEAN,
                                time_taken_ms INT,
                                points_awarded INT DEFAULT 0,
                                answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);