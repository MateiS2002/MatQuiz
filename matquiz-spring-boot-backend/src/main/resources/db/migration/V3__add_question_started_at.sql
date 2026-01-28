ALTER TABLE game_rooms
    ADD question_started_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE game_rooms
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE game_rooms
    ALTER COLUMN current_question_index SET NOT NULL;

ALTER TABLE game_rooms
    DROP COLUMN difficulty;

ALTER TABLE game_rooms
    DROP COLUMN status;

ALTER TABLE game_rooms
    ADD difficulty SMALLINT NOT NULL default 0;

ALTER TABLE game_rooms
    ALTER COLUMN question_count SET NOT NULL;

ALTER TABLE game_rooms
    ADD status SMALLINT NOT NULL default 0;