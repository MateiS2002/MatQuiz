/**
 * V2: Add Role support to Users
 * Defaults to 'ROLE_USER' for standard players.
 */

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) DEFAULT 'ROLE_USER' NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT hc_role_check CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));