-- Initialize database schema for Grondona application

-- Remove existing tables
drop table if exists match_predictions cascade;
drop table if exists award_predictions cascade;
drop table if exists matches cascade;
drop table if exists teams cascade;
drop table if exists players cascade;
drop table if exists group_users cascade;
drop table if exists groups cascade;
drop table if exists tournaments cascade;
drop table if exists users cascade;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fullname VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(32) NOT NULL,
    permissions VARCHAR(20) NOT NULL DEFAULT 'USER',
    unique_predictions BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE users IS 'User accounts table';
COMMENT ON COLUMN users.id IS 'Unique identifier for the user';
COMMENT ON COLUMN users.fullname IS 'Full name of the user';
COMMENT ON COLUMN users.username IS 'Unique username for authentication';
COMMENT ON COLUMN users.email IS 'Unique email address';
COMMENT ON COLUMN users.password_hash IS 'MD5 hashed password';
COMMENT ON COLUMN users.permissions IS 'Access level granted to an user (can be either USER or SUPERUSER)';
COMMENT ON COLUMN users.created_at IS 'Timestamp when the user was created';
COMMENT ON COLUMN users.updated_at IS 'Timestamp when the user was last updated';
COMMENT ON COLUMN users.deleted_at IS 'Timestamp when the user was deleted';

-- Seed default users
INSERT INTO users (id, fullname, username, email, password_hash, permissions) VALUES
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'Cristian Raña', 'cris', 'cris@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'SUPERUSER'),
    ('60635292-4a13-43d8-b976-b2e292020deb', 'Lautaro Chamorro', 'chas', 'chas@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4fc682de-233f-4b0f-b4c3-4ee0f5716675', 'Manuel Domínguez', 'manu', 'manu@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('56118705-5d57-4a6d-9f38-46606c78dbd6', 'Federico Cornago', 'corna', 'corna@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('a2618ce3-03a0-4f81-bb0b-010c0245a65b', 'Gastón Macrini', 'macro', 'macro@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('2b67aaa9-2ecf-4d21-9ce1-e378337b6adb', 'Gastón Añón', 'añon', 'añon@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('b7d358aa-42c0-4b22-9da2-ed292a00ee47', 'Federico Groisman', 'grois', 'grois@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4cb252f7-9dac-4249-a9a2-b45d5234d384', 'Franco Rapallini', 'fran', 'fran@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('bdbf29ee-cfda-4d02-9928-af93ebd40892', 'Facundo Gandara', 'rifle', 'rifle@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4a97633c-649b-4315-91a9-f995dc950171', 'Germán Raña', 'germán', 'german@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('dab4229a-e438-4d11-8f29-26320991848f', 'Ariel Canteros', 'ariel', 'ariel@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('23295782-bc35-4d70-892b-37a771620bc7', 'Camila Ivanovich', 'cami', 'cami@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER')
ON CONFLICT (id) DO NOTHING;

-- Create tournaments table
CREATE TABLE IF NOT EXISTS tournaments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(256) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    awards JSONB DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_tournaments_name ON tournaments(name) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE tournaments IS 'Tournaments table';
COMMENT ON COLUMN tournaments.id IS 'Unique identifier for the tournament';
COMMENT ON COLUMN tournaments.name IS 'Name of the tournament';
COMMENT ON COLUMN tournaments.status IS 'Status of the tournament (can be either NOT_STARTED, IN_PROGRESS or FINISHED)';
COMMENT ON COLUMN tournaments.awards IS 'Awards of the tournament (populated at the end of it)';
COMMENT ON COLUMN tournaments.created_at IS 'Timestamp when the tournament was created';
COMMENT ON COLUMN tournaments.updated_at IS 'Timestamp when the tournament was last updated';
COMMENT ON COLUMN tournaments.deleted_at IS 'Timestamp when the tournament was deleted';

-- Seed default tournaments
INSERT INTO tournaments (id, name, status) VALUES
    ('28652183-a2d6-4f33-a624-0d24645ce3cd', 'Copa del Mundo 2026', 'NOT_STARTED')
ON CONFLICT (id) DO NOTHING;

-- Create groups table
CREATE TABLE IF NOT EXISTS groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL UNIQUE,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    max_members INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_name ON groups(tournament_id, name) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE groups IS 'Groups table';
COMMENT ON COLUMN groups.id IS 'Unique identifier for the group';
COMMENT ON COLUMN groups.tournament_id IS 'Reference to the tournament';
COMMENT ON COLUMN groups.name IS 'Unique group name';
COMMENT ON COLUMN groups.is_private IS 'Whether the group is private';
COMMENT ON COLUMN groups.max_members IS 'Maximum number of members allowed';
COMMENT ON COLUMN groups.created_at IS 'Timestamp when the group was created';
COMMENT ON COLUMN groups.updated_at IS 'Timestamp when the group was last updated';
COMMENT ON COLUMN groups.deleted_at IS 'Timestamp when the group was deleted';

-- Seed default groups
INSERT INTO groups (id, tournament_id, name, is_private, max_members) VALUES
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'General', TRUE, 50),
    ('7c9e6679-7425-40de-944b-e07fc1f90ae7', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'EPO', TRUE, 25),
    ('b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Baldosa', TRUE, 27),
    ('e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Maldolar', TRUE, 12),
    ('8158a607-97b3-47db-8382-92d878358b9c', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Familia', TRUE, 20)
ON CONFLICT (id) DO NOTHING;

-- Create group_users table (membership)
CREATE TABLE IF NOT EXISTS group_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    rank INTEGER DEFAULT NULL,
    points FLOAT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP DEFAULT NULL,
    amount_bonus INTEGER DEFAULT 0,
    amount_correct INTEGER DEFAULT 0,
    amount_partial INTEGER DEFAULT 0,
    last_predictions VARCHAR(20)[] NOT NULL DEFAULT '{}',
    predicted_awards JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_group_users_user_id ON group_users(user_id);
CREATE INDEX IF NOT EXISTS idx_group_users_group_id ON group_users(group_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_group_users_uniqueness ON group_users(user_id, group_id) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE group_users IS 'Group membership table';
COMMENT ON COLUMN group_users.id IS 'Identifier of the reference';
COMMENT ON COLUMN group_users.user_id IS 'Reference to the user';
COMMENT ON COLUMN group_users.group_id IS 'Reference to the group';
COMMENT ON COLUMN group_users.role IS 'Role of the user in the group (can be either MEMBER, ADMIN or OWNER)';
COMMENT ON COLUMN group_users.rank IS 'Ranking of the user in the group';
COMMENT ON COLUMN group_users.points IS 'Amount of points of the given user in the given tournament';
COMMENT ON COLUMN group_users.joined_at IS 'Timestamp when the user joined the group';
COMMENT ON COLUMN group_users.amount_bonus IS 'Amount of BONUS predictions';
COMMENT ON COLUMN group_users.amount_correct IS 'Amount of CORRECT predictions';
COMMENT ON COLUMN group_users.amount_partial IS 'Amount of PARTIAL predictions';
COMMENT ON COLUMN group_users.last_predictions IS 'Status of the last 5 predictions';
COMMENT ON COLUMN group_users.predicted_awards IS 'Awards predicted for the tournament';
COMMENT ON COLUMN group_users.created_at IS 'Timestamp when the membership was created';
COMMENT ON COLUMN group_users.updated_at IS 'Timestamp when the membership was updated';
COMMENT ON COLUMN group_users.deleted_at IS 'Timestamp when the membership was deleted';

-- Seed default members
INSERT INTO group_users (user_id, group_id, role) VALUES
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', '8158a607-97b3-47db-8382-92d878358b9c', 'OWNER'),
    ('60635292-4a13-43d8-b976-b2e292020deb', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'ADMIN'),
    ('4fc682de-233f-4b0f-b4c3-4ee0f5716675', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('56118705-5d57-4a6d-9f38-46606c78dbd6', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('a2618ce3-03a0-4f81-bb0b-010c0245a65b', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'ADMIN'),
    ('2b67aaa9-2ecf-4d21-9ce1-e378337b6adb', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'MEMBER'),
    ('b7d358aa-42c0-4b22-9da2-ed292a00ee47', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'MEMBER'),
    ('4cb252f7-9dac-4249-a9a2-b45d5234d384', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'ADMIN'),
    ('bdbf29ee-cfda-4d02-9928-af93ebd40892', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'MEMBER'),
    ('4a97633c-649b-4315-91a9-f995dc950171', '8158a607-97b3-47db-8382-92d878358b9c', 'ADMIN'),
    ('dab4229a-e438-4d11-8f29-26320991848f', '8158a607-97b3-47db-8382-92d878358b9c', 'MEMBER'),
    ('23295782-bc35-4d70-892b-37a771620bc7', '8158a607-97b3-47db-8382-92d878358b9c', 'MEMBER'),
    ('60635292-4a13-43d8-b976-b2e292020deb', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'ADMIN'),
    ('4fc682de-233f-4b0f-b4c3-4ee0f5716675', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('56118705-5d57-4a6d-9f38-46606c78dbd6', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('a2618ce3-03a0-4f81-bb0b-010c0245a65b', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('2b67aaa9-2ecf-4d21-9ce1-e378337b6adb', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('b7d358aa-42c0-4b22-9da2-ed292a00ee47', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('4cb252f7-9dac-4249-a9a2-b45d5234d384', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('bdbf29ee-cfda-4d02-9928-af93ebd40892', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('4a97633c-649b-4315-91a9-f995dc950171', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('dab4229a-e438-4d11-8f29-26320991848f', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER'),
    ('23295782-bc35-4d70-892b-37a771620bc7', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'MEMBER')
ON CONFLICT (id) DO NOTHING;

-- Create teams table
CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(5) NOT NULL,
    icon TEXT DEFAULT 'https://flagicons.lipis.dev/flags/4x3/xx.svg',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_teams_code ON teams(code, tournament_id) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE teams IS 'Teams table';
COMMENT ON COLUMN teams.id IS 'Unique identifier for the team';
COMMENT ON COLUMN teams.tournament_id IS 'Reference to the tournament';
COMMENT ON COLUMN teams.name IS 'Name of the team';
COMMENT ON COLUMN teams.code IS 'FIFA code of the team';
COMMENT ON COLUMN teams.icon IS 'URL with the team icon';
COMMENT ON COLUMN teams.created_at IS 'Timestamp when the team was created';
COMMENT ON COLUMN teams.updated_at IS 'Timestamp when the team was last updated';
COMMENT ON COLUMN teams.deleted_at IS 'Timestamp when the team was deleted';

-- Seed default tournaments
INSERT INTO teams (id, tournament_id, name, code, icon) VALUES
    ('6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01', '28652183-a2d6-4f33-a624-0d24645ce3cd','Alemania', 'GER', 'https://flagcdn.com/w40/de.png'),
    ('b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02', '28652183-a2d6-4f33-a624-0d24645ce3cd','Arabia Saudita', 'KSA', 'https://flagcdn.com/w40/sa.png'),
    ('1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03', '28652183-a2d6-4f33-a624-0d24645ce3cd','Argelia', 'ALG', 'https://flagcdn.com/w40/dz.png'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04', '28652183-a2d6-4f33-a624-0d24645ce3cd','Argentina', 'ARG', 'https://flagcdn.com/w40/ar.png'),
    ('3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05', '28652183-a2d6-4f33-a624-0d24645ce3cd','Australia', 'AUS', 'https://flagcdn.com/w40/au.png'),
    ('7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06', '28652183-a2d6-4f33-a624-0d24645ce3cd','Austria', 'AUT', 'https://flagcdn.com/w40/at.png'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07', '28652183-a2d6-4f33-a624-0d24645ce3cd','Brasil', 'BRA', 'https://flagcdn.com/w40/br.png'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', '28652183-a2d6-4f33-a624-0d24645ce3cd','Bélgica', 'BEL', 'https://flagcdn.com/w40/be.png'),
    ('4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09', '28652183-a2d6-4f33-a624-0d24645ce3cd','Cabo Verde', 'CPV', 'https://flagcdn.com/w40/cv.png'),
    ('5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10', '28652183-a2d6-4f33-a624-0d24645ce3cd','Canadá', 'CAN', 'https://flagcdn.com/w40/ca.png'),
    ('c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11', '28652183-a2d6-4f33-a624-0d24645ce3cd','Catar', 'QAT', 'https://flagcdn.com/w40/qa.png'),
    ('d7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12', '28652183-a2d6-4f33-a624-0d24645ce3cd','Colombia', 'COL', 'https://flagcdn.com/w40/co.png'),
    ('e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13', '28652183-a2d6-4f33-a624-0d24645ce3cd','Corea del Sur', 'KOR', 'https://flagcdn.com/w40/kr.png'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', '28652183-a2d6-4f33-a624-0d24645ce3cd','Costa de Marfil', 'CIV', 'https://flagcdn.com/w40/ci.png'),
    ('a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15', '28652183-a2d6-4f33-a624-0d24645ce3cd','Croacia', 'CRO', 'https://flagcdn.com/w40/hr.png'),
    ('b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16', '28652183-a2d6-4f33-a624-0d24645ce3cd','Curazao', 'CUW', 'https://flagcdn.com/w40/cw.png'),
    ('c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17', '28652183-a2d6-4f33-a624-0d24645ce3cd','Ecuador', 'ECU', 'https://flagcdn.com/w40/ec.png'),
    ('d5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18', '28652183-a2d6-4f33-a624-0d24645ce3cd','Egipto', 'EGY', 'https://flagcdn.com/w40/eg.png'),
    ('e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19', '28652183-a2d6-4f33-a624-0d24645ce3cd','Escocia', 'SCO', 'https://flagcdn.com/w40/gb-sct.png'),
    ('f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20', '28652183-a2d6-4f33-a624-0d24645ce3cd','España', 'ESP', 'https://flagcdn.com/w40/es.png'),
    ('a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21', '28652183-a2d6-4f33-a624-0d24645ce3cd','Estados Unidos', 'USA', 'https://flagcdn.com/w40/us.png'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', '28652183-a2d6-4f33-a624-0d24645ce3cd','Francia', 'FRA', 'https://flagcdn.com/w40/fr.png'),
    ('c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23', '28652183-a2d6-4f33-a624-0d24645ce3cd','Ghana', 'GHA', 'https://flagcdn.com/w40/gh.png'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', '28652183-a2d6-4f33-a624-0d24645ce3cd','Haití', 'HAI', 'https://flagcdn.com/w40/ht.png'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', '28652183-a2d6-4f33-a624-0d24645ce3cd','Inglaterra', 'ENG', 'https://flagcdn.com/w40/gb-eng.png'),
    ('f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26', '28652183-a2d6-4f33-a624-0d24645ce3cd','Irán', 'IRN', 'https://flagcdn.com/w40/ir.png'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', '28652183-a2d6-4f33-a624-0d24645ce3cd','Japón', 'JPN', 'https://flagcdn.com/w40/jp.png'),
    ('b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28', '28652183-a2d6-4f33-a624-0d24645ce3cd','Jordania', 'JOR', 'https://flagcdn.com/w40/jo.png'),
    ('c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29', '28652183-a2d6-4f33-a624-0d24645ce3cd','Marruecos', 'MAR', 'https://flagcdn.com/w40/ma.png'),
    ('d3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30', '28652183-a2d6-4f33-a624-0d24645ce3cd','México', 'MEX', 'https://flagcdn.com/w40/mx.png'),
    ('e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31', '28652183-a2d6-4f33-a624-0d24645ce3cd','Noruega', 'NOR', 'https://flagcdn.com/w40/no.png'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', '28652183-a2d6-4f33-a624-0d24645ce3cd','Nueva Zelanda', 'NZL', 'https://flagcdn.com/w40/nz.png'),
    ('a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33', '28652183-a2d6-4f33-a624-0d24645ce3cd','Países Bajos', 'NED', 'https://flagcdn.com/w40/nl.png'),
    ('b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34', '28652183-a2d6-4f33-a624-0d24645ce3cd','Panamá', 'PAN', 'https://flagcdn.com/w40/pa.png'),
    ('c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35', '28652183-a2d6-4f33-a624-0d24645ce3cd','Paraguay', 'PAR', 'https://flagcdn.com/w40/py.png'),
    ('d7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36', '28652183-a2d6-4f33-a624-0d24645ce3cd','Portugal', 'POR', 'https://flagcdn.com/w40/pt.png'),
    ('e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37', '28652183-a2d6-4f33-a624-0d24645ce3cd','Senegal', 'SEN', 'https://flagcdn.com/w40/sn.png'),
    ('f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38', '28652183-a2d6-4f33-a624-0d24645ce3cd','Sudáfrica', 'RSA', 'https://flagcdn.com/w40/za.png'),
    ('a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39', '28652183-a2d6-4f33-a624-0d24645ce3cd','Suiza', 'SUI', 'https://flagcdn.com/w40/ch.png'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', '28652183-a2d6-4f33-a624-0d24645ce3cd','Túnez', 'TUN', 'https://flagcdn.com/w40/tn.png'),
    ('c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41', '28652183-a2d6-4f33-a624-0d24645ce3cd','Uruguay', 'URU', 'https://flagcdn.com/w40/uy.png'),
    ('d9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42', '28652183-a2d6-4f33-a624-0d24645ce3cd','Uzbekistán', 'UZB', 'https://flagcdn.com/w40/uz.png'),
    ('8f251495-81ba-4724-b575-f7ebecf213c4', '28652183-a2d6-4f33-a624-0d24645ce3cd','R.D. del Congo', 'COD', 'https://flagcdn.com/w40/cd.png'),
    ('da0e5c75-ba1c-4090-bbba-ad57d0e3b153', '28652183-a2d6-4f33-a624-0d24645ce3cd','Irak', 'IRQ', 'https://flagcdn.com/w40/iq.png'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', '28652183-a2d6-4f33-a624-0d24645ce3cd','Bosnia-Herzegovina', 'BIH', 'https://flagcdn.com/w40/ba.png'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', '28652183-a2d6-4f33-a624-0d24645ce3cd','Suecia', 'SWE', 'https://flagcdn.com/w40/se.png'),
    ('07782a28-4f6d-4037-86b8-ccff4c2de218', '28652183-a2d6-4f33-a624-0d24645ce3cd','Turquía', 'TUR', 'https://flagcdn.com/w40/tr.png'),
    ('219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4', '28652183-a2d6-4f33-a624-0d24645ce3cd','Chequia', 'CZE', 'https://flagcdn.com/w40/cz.png')
ON CONFLICT (id) DO NOTHING;

-- Create teams table
CREATE TABLE IF NOT EXISTS players (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      position VARCHAR(20) NOT NULL,
      birthdate DATE NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_players_team ON players(name, team_id) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE players IS 'Teams table';
COMMENT ON COLUMN players.id IS 'Unique identifier for the player';
COMMENT ON COLUMN players.team_id IS 'Reference to the team';
COMMENT ON COLUMN players.name IS 'Name of the player';
COMMENT ON COLUMN players.position IS 'Position of the player';
COMMENT ON COLUMN players.birthdate IS 'Birthdate of the player';
COMMENT ON COLUMN players.created_at IS 'Timestamp when the player was created';
COMMENT ON COLUMN players.updated_at IS 'Timestamp when the player was last updated';
COMMENT ON COLUMN players.deleted_at IS 'Timestamp when the player was deleted';

INSERT INTO players (team_id, name, position, birthdate) VALUES
    -- SWE
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Viktor Johansson', 'GOALKEEPER', '1998-09-14'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Jacob Widell Zetterstrom', 'GOALKEEPER', '1998-07-11'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Gustaf Lagerbielke', 'DEFENDER', '2000-04-10'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Victor Lindelof', 'DEFENDER', '1994-07-17'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Gabriel Gudmundsson', 'DEFENDER', '1999-04-29'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Daniel Svensson', 'DEFENDER', '2002-02-12'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Elliot Stroud', 'DEFENDER', '2002-06-22'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Carl Starfelt', 'DEFENDER', '1995-06-01'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Isak Hien', 'DEFENDER', '1999-01-13'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Emil Holm', 'DEFENDER', '2000-05-13'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Hjalmar Ekdal', 'DEFENDER', '1998-10-21'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Eric Smith ', 'DEFENDER', '1997-01-08'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Lucas Bergvall', 'MIDFIELDER', '2006-02-02'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Jesper Karlstrom', 'MIDFIELDER', '1995-06-21'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Yasin Ayari', 'MIDFIELDER', '2003-10-06'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Mattias Svanberg', 'MIDFIELDER', '1991-01-05'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Besfort Zeneli', 'MIDFIELDER', '2002-11-21'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Ken Sema', 'MIDFIELDER', '1993-09-30'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Gustaf Nilsson', 'FORWARD', '1997-05-23'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Benjamin Nygren', 'FORWARD', '2001-07-08'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Anthony Elanga', 'FORWARD', '2002-04-27'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Viktor Gyokeres', 'FORWARD', '1998-06-04'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Taha Ali', 'FORWARD', '1998-07-01'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Alexander Isak', 'FORWARD', '1999-09-21'),
    ('8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'Alexander Bernhardsson', 'FORWARD', '1998-09-08'),
    -- BIH
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Nikola Vasilj', 'GOALKEEPER', '1995-12-02'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Martin Zlomislić', 'GOALKEEPER', '1998-08-16'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Osman Hadžikić', 'GOALKEEPER', '1996-03-12'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Sead Kolašinac', 'DEFENDER', '1993-06-20'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Dennis Hadžikadunić', 'DEFENDER', '1998-07-09'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Amar Dedić', 'DEFENDER', '2002-08-18'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Nikola Katić', 'DEFENDER', '1996-10-10'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Tarik Muharemović', 'DEFENDER', '2003-02-28'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Nihad Mujakić', 'DEFENDER', '1998-04-15'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Stjepan Radeljić', 'DEFENDER', '1997-09-05'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Nidal Čelik', 'DEFENDER', '2006-06-24'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Amir Hadžiahmetović', 'MIDFIELDER', '1997-03-08'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Benjamin Tahirović', 'MIDFIELDER', '2003-03-03'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Armin Gigović', 'MIDFIELDER', '2002-04-06'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Dženis Burnić', 'MIDFIELDER', '1998-05-22'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Ivan Bašić', 'MIDFIELDER', '2002-04-30'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Esmir Bajraktarević', 'MIDFIELDER', '2005-03-10'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Amar Memić', 'MIDFIELDER', '2001-01-20'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Ivan Šunjić', 'MIDFIELDER', '1996-10-09'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Kerim Alajbegović', 'MIDFIELDER', '2007-04-18'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Ermin Mahmić', 'MIDFIELDER', '2003-03-06'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Edin Džeko', 'FORWARD', '1986-03-17'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Ermedin Demirović', 'FORWARD', '1998-03-25'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Samed Baždar', 'FORWARD', '2004-01-31'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Haris Tabaković', 'FORWARD', '1994-06-20'),
    ('fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'Jovo Lukić', 'FORWARD', '1999-07-20'),
    -- HAI
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'GOALKEEPER', 'Johnny Placide', '1988-01-29'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'GOALKEEPER', 'Alexandre Pierre', '2001-02-25'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'GOALKEEPER', 'Josué Duverger', '2000-04-12'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Carlens Arcus', '1996-06-28'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Wilguens Paugain', '2001-06-17'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Duke Lacroix', '1993-10-14'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Martin Experience', '1997-05-16'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Jean-Kévin Duverne', '1997-07-12'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Ricardo Adé', '1990-05-21'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Hannes Delcroix', '1999-02-28'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'DEFENDER', 'Keeto Thermoncy', '2001-10-28'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Leverton Pierre', '1998-09-09'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Carl-Fred Sainthe', '2002-07-19'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Jean-Jacques Danley', '1999-09-17'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Jeanricner Bellegarde', '1998-06-27'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Pierre Woodensky', '2000-04-29'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'MIDFIELDER', 'Dominique Simon', '1995-01-20'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Louicius Deedson', '2001-02-27'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Ruben Providence', '2001-07-07'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Josué Casimir', '2001-09-24'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Derrick Etienne', '1996-11-25'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Wilson Isidor', '2000-08-27'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Duckens Nazon', '1994-04-07'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Frantzdy Pierrot', '1995-03-20'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Yassin Fortune', '1999-01-30'),
    ('d1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'FORWARD', 'Lenny Joseph', '2000-10-12'),
    -- CIV
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'GOALKEEPER', 'Yahia Fofana', '2000-08-21'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'GOALKEEPER', 'Mohamed Koné', '2001-03-12'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'GOALKEEPER', 'Alban Lafont', '1999-01-23'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Emmanuel Agbadou', '1997-06-17'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Clément Akpa', '2001-11-24'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Ousmane Diomande', '2003-12-04'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Guéla Doué', '2002-10-17'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Ghislain Konan', '1995-12-27'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Odilon Kossounou', '2001-01-04'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Evan Ndicka', '1999-08-20'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'DEFENDER', 'Wilfried Singo', '2000-12-25'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Seko Fofana', '1995-05-07'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Parfait Guiagon', '2001-01-22'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Christ Inao Oulai', '1999-12-20'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Franck Kessié', '1996-12-19'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Ibrahim Sangaré', '1997-12-02'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'MIDFIELDER', 'Jean Michaël Seri', '1991-07-19'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Simon Adingra', '2002-01-01'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Ange-Yoan Bonny', '2003-10-25'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Amad Diallo', '2002-07-11'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Oumar Diakité', '2003-12-20'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Yan Diomande', '2006-11-14'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Evann Guessand', '2001-07-01'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Nicolas Pépé', '1995-05-29'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Bazoumana Touré', '2006-03-02'),
    ('f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'FORWARD', 'Elye Wahi', '2003-01-02'),
    -- JPN
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'GOALKEEPER', 'Zion Suzuki', '2002-08-21'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'GOALKEEPER', 'Keisuke Osako', '1999-07-28'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'GOALKEEPER', 'Tomoki Hayakawa', '1999-03-03'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Yuto Nagamoto', '1986-09-12'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Shogo Taniguchi', '1991-07-15'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Ko Itakura', '1997-01-27'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Takehiro Tomiyasu', '1998-11-05'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Ayumu Seko', '2000-06-07'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Yukinari Sugawara', '2000-06-28'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Hiroki Ito', '1999-05-12'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Junnosuke Suzuki', '2003-07-12'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'DEFENDER', 'Tsuyoshi Watanabe', '1997-02-05'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'MIDFIELDER', 'Kaishu Sano', '2000-12-30'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'MIDFIELDER', 'Wataru Endo', '1993-02-09'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'MIDFIELDER', 'Ao Tanaka', '1998-09-10'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'MIDFIELDER', 'Daichi Kamada', '1996-08-05'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'MIDFIELDER', 'Junya Ito', '1993-03-09'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Keito Nakamura', '2000-07-28'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Daizen Maeda', '1997-10-20'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Koki Ogawa', '1997-08-08'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Takefusa Kubo', '2001-06-04'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Yuito Suzuki', '2001-10-25'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Kento Shiogai', '2005-03-26'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Keisuke Goto', '2005-06-03'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Ritsu Doan', '1998-06-16'),
    ('a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', 'FORWARD', 'Ayase Ueda', '1998-08-28'),
    -- TUN
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'GOALKEEPER', 'Aymen Dahmen', '1997-01-28'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'GOALKEEPER', 'A. Chamakh', '2002-04-07'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'GOALKEEPER', 'Sabri Ben Hassen', '1998-07-11'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Van Valery', '1999-10-22'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Moutaz Neffati', '2004-01-22'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Dylan Bronn', '1995-06-19'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Montassar Talbi', '1998-05-26'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Omar Rekik', '2001-12-20'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Adem Arous', '2003-01-15'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Raed Chikhaoui', '1999-02-02'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Ali Abdi', '1993-12-20'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'DEFENDER', 'Mohamed Amine Ben Hmida', '1995-03-06'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Ellyes Skhiri', '1995-05-10'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Mohamed Hadj Mahmoud', '2000-05-25'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Rani Khedira', '1994-01-27'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Anis Ben Slimane', '2001-03-16'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Mortadha Ben Ouanes', '1994-07-16'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Ismaël Gharbi', '2004-04-10'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'MIDFIELDER', 'Hannibal Mejbri', '2003-01-21'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Khalil Ayari', '2005-01-09'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Elias Achouri', '1999-02-10'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Elias Saad', '1999-12-27'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Firas Chaouat', '1996-05-20'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Hazem Mastouri', '1997-03-16'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Rayan Elloumi', '2005-01-18'),
    ('b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'FORWARD', 'Sebastian Tounekti', '2002-07-13'),
    -- BEL
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'GOALKEEPER', 'Thibaut Courtois', '1992-05-11'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'GOALKEEPER', 'Senne Lammens', '2002-07-07'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'GOALKEEPER', 'Mike Penders', '2005-07-31'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Zeno Debast', '2003-10-24'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Arthur Theate', '2000-05-25'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Koni De Winter', '2002-06-12'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Brandon Mechele', '1993-01-28'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Nathan Ngoy', '2003-03-10'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Thomas Meunier', '1991-09-12'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Timothy Castagne', '1995-12-05'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Maxim De Cuyper', '2000-12-22'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'DEFENDER', 'Joaquin Seys', '2004-02-08'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Kevin De Bruyne', '1991-06-28'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Amadou Onana', '2001-08-16'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Youri Tielemans', '1997-05-07'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Hans Vanaken', '1992-08-24'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Nicolas Raskin', '2001-02-23'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'MIDFIELDER', 'Axel Witsel', '1989-01-12'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Jérémy Doku', '2002-05-27'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Leandro Trossard', '1994-12-04'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Alexis Saelemaekers', '1999-06-27'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Dodi Lukebakio', '1997-09-24'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Romelu Lukaku', '1993-05-13'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Charles De Ketelaere', '2001-03-10'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Matias Fernández-Pardo', '2005-02-03'),
    ('8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'FORWARD', 'Diego Moreira', '2004-08-06'),
-- NZL
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'GOALKEEPER', 'Max Crocombe', '1993-08-12'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'GOALKEEPER', 'Alex Paulsen', '2002-07-26'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'GOALKEEPER', 'Michael Woud', '1999-01-16'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Tyler Bindon', '2005-01-27'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Michael Boxall', '1988-08-18'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Liberato Cacace', '2000-09-27'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Francis De Vries', '1995-11-28'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Callan Elliot', '1999-01-07'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Tim Payne', '1994-01-10'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Nando Pijnaker', '1999-02-25'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Tommy Smith', '1990-03-31'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'DEFENDER', 'Finn Surman', '2003-09-23'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'MIDFIELDER', 'Lachlan Bayliss', '2002-04-24'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'MIDFIELDER', 'Joe Bell', '1999-04-27'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'MIDFIELDER', 'Alex Rufer', '1996-06-08'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'MIDFIELDER', 'Marko Stamenić', '2002-02-19'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'MIDFIELDER', 'Ryan Thomas', '1994-12-20'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Kosta Barbarouses', '1990-02-19'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Matt Garbett', '2002-04-13'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Eli Just', '2000-03-12'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Callum McCowatt', '1999-04-30'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Ben Old', '2002-08-13'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Jesse Randall', '2002-10-11'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Sarpreet Singh', '1999-02-20'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Ben Waine', '2001-06-11'),
    ('f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'FORWARD', 'Chris Wood', '1991-12-07'),
    -- FRA
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'GOALKEEPER', 'Mike Maignan', '1995-07-03'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'GOALKEEPER', 'Brice Samba', '1994-04-25'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'GOALKEEPER', 'Robin Risser', '2004-12-02'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'William Saliba', '2001-03-24'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Dayot Upamecano', '1998-10-27'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Ibrahima Konaté', '1999-05-25'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Maxence Lacroix', '2000-04-06'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Jules Koundé', '1998-11-12'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Malo Gusto', '2003-05-19'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Lucas Digne', '1993-07-20'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Lucas Hernández', '1996-02-14'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'DEFENDER', 'Theo Hernández', '1997-10-06'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'MIDFIELDER', 'Aurélien Tchouaméni', '2000-01-27'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'MIDFIELDER', 'Warren Zaïre-Emery', '2006-03-08'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'MIDFIELDER', 'Manu Koné', '2001-05-17'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'MIDFIELDER', 'N’Golo Kanté', '1991-03-29'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'MIDFIELDER', 'Adrien Rabiot', '1995-04-03'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Michael Olise', '2001-12-12'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Rayan Cherki', '2003-08-17'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Ousmane Dembélé', '1997-05-15'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Désiré Doué', '2005-06-03'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Kylian Mbappé', '1998-12-20'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Marcus Thuram', '1997-08-06'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Bradley Barcola', '2002-09-02'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Maghnes Akliouche', '2002-02-25'),
    ('b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'FORWARD', 'Jean-Philippe Mateta', '1997-06-28'),
    -- ENG
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Dean Henderson', 'GOALKEEPER', '1997-03-12'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Jordan Pickford', 'GOALKEEPER', '1994-03-07'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Aaron Ramsdale', 'GOALKEEPER', '1998-05-14'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Lewis Dunk', 'DEFENDER', '1991-11-21'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Joe Gomez', 'DEFENDER', '1997-05-23'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Marc Guehi', 'DEFENDER', '2000-07-13'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Ezri Konsa', 'DEFENDER', '1997-10-23'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Luke Shaw', 'DEFENDER', '1995-07-12'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'John Stones', 'DEFENDER', '1994-05-28'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Kieran Trippier', 'DEFENDER', '1990-09-19'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Kyle Walker', 'DEFENDER', '1990-05-28'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Trent Alexander-Arnold', 'MIDFIELDER', '1998-10-07'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Reece James', 'MIDFIELDER', '1999-12-08'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Kobbie Mainoo', 'MIDFIELDER', '2005-04-19'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Declan Rice', 'MIDFIELDER', '1999-01-14'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Adam Wharton', 'MIDFIELDER', '2004-02-06'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Jude Bellingham', 'FORWARD', '2003-06-29'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Jarrod Bowen', 'FORWARD', '1996-12-20'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Eberechi Eze', 'FORWARD', '1998-06-29'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Phil Foden', 'FORWARD', '2000-05-28'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Anthony Gordon', 'FORWARD', '2001-02-24'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Harry Kane', 'FORWARD', '1993-07-28'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Cole Palmer', 'FORWARD', '2002-05-06'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Bukayo Saka', 'FORWARD', '2001-09-05'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Mason Greenwood', 'FORWARD', '2001-10-01'),
    ('e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'Ollie Watkins', 'FORWARD', '1995-12-30'),
    -- THIS
    -- IS
    -- FAKE
    -- DATA
    ('6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01','Joshua Kimmich', 'FORWARD', '1995-02-08'),
    ('6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01','Florian Wirtz', 'FORWARD', '2003-05-03'),
    ('6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01','Manuel Neuer', 'GOALKEEPER', '1986-03-27'),
    ('b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02','Salem Al-Dawsari', 'FORWARD', '1991-08-19'),
    ('b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02','Ali Lajami', 'GOALKEEPER', '1996-04-24'),
    ('b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02','Mohammed Al-Owais', 'FORWARD', '1991-10-10'),
    ('1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03','Riyad Mahrez', 'FORWARD', '1991-02-25'),
    ('1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03','Anis Hadj Moussa', 'FORWARD', '2002-02-11'),
    ('1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03','Anthony Mandréa', 'GOALKEEPER', '1996-12-25'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Lionel Messi', 'FORWARD', '1987-06-21'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Julián Álvarez', 'FORWARD', '2000-01-31'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Alexis Mac Allister', 'FORWARD', '1998-12-24'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Franco Mastantuono', 'FORWARD', '2007-08-14'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Emiliano Martínez', 'GOALKEEPER', '1992-09-02'),
    ('9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04','Juan Musso', 'GOALKEEPER', '1994-05-06'),
    ('3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05','Riley McGree', 'FORWARD', '1998-11-02'),
    ('3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05','Ajdin Hrustic', 'FORWARD', '1996-07-05'),
    ('3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05','Mathew Ryan', 'GOALKEEPER', '1992-04-08'),
    ('7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06','David Alaba', 'FORWARD', '1992-06-24'),
    ('7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06','Konrad Laimer', 'FORWARD', '1997-05-27'),
    ('7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06','Alexander Schlager', 'GOALKEEPER', '1996-02-01'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Vinicius Jr', 'FORWARD', '2000-07-12'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Raphinha', 'FORWARD', '1996-12-14'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Endrick', 'FORWARD', '2006-07-11'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Estevão', 'FORWARD', '2007-04-24'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Alisson Becker', 'GOALKEEPER', '1992-10-02'),
    ('2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07','Ederson', 'GOALKEEPER', '1993-08-17'),
    ('4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09','Ryan Mendes', 'FORWARD', '1990-01-08'),
    ('4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09','Jovane Cabral', 'FORWARD', '1998-06-14'),
    ('4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09','Vozinha', 'GOALKEEPER', '1986-06-03'),
    ('5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10','Alphonso Davies', 'FORWARD', '2000-11-02'),
    ('5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10','Jonathan David', 'FORWARD', '2000-01-14'),
    ('5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10','Maxime Crépeau', 'GOALKEEPER', '1994-05-11'),
    ('c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11','Akram Afif', 'FORWARD', '1996-11-18'),
    ('c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11','Almoez Ali', 'FORWARD', '1996-08-19'),
    ('c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11','Meshaal Barsham', 'GOALKEEPER', '1998-02-14'),
    ('d7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12','Luis Díaz', 'FORWARD', '1997-01-13'),
    ('d7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12','James Rodríguez', 'FORWARD', '1991-07-12'),
    ('d7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12','David Ospina', 'GOALKEEPER', '1988-08-31'),
    ('e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13','Son Heung-Min', 'FORWARD', '1992-07-08'),
    ('e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13','Lee Kang-in', 'FORWARD', '2001-02-19'),
    ('e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13','Jo Hyeon-woo', 'GOALKEEPER', '1991-09-25'),
    ('a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15','Luka Modrić', 'FORWARD', '1985-09-09'),
    ('a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15','Joško Gvardiol', 'FORWARD', '2002-01-21'),
    ('a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15','Dominik Livaković', 'GOALKEEPER', '1995-01-09'),
    ('b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16','Tahith Chong', 'FORWARD', '1999-12-04'),
    ('b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16','Leandro Bacuna', 'FORWARD', '1991-08-21'),
    ('b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16','Eloy Room', 'GOALKEEPER', '1989-02-06'),
    ('c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17','Moisés Caicedo', 'FORWARD', '2001-11-02'),
    ('c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17','Willian Pacho', 'FORWARD', '2001-11-16'),
    ('c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17','Hernán Galíndez', 'GOALKEEPER', '1987-03-30'),
    ('d5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18','Mohamed Salah', 'FORWARD', '1992-06-15'),
    ('d5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18','Omar Marmoush', 'FORWARD', '1999-02-07'),
    ('d5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18','Mohamed El-Shenawy', 'GOALKEEPER', '1988-12-18'),
    ('e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19','Scott McTominay', 'FORWARD', '1992-12-08'),
    ('e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19','Andrew Robertson', 'FORWARD', '1994-03-11'),
    ('e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19','Angus Gunn', 'GOALKEEPER', '1996-01-22'),
    ('f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20','Lamine Yamal', 'FORWARD', '2007-07-13'),
    ('f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20','Pedri', 'FORWARD', '2002-11-25'),
    ('f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20','Pau Cubarsí', 'FORWARD', '2007-01-22'),
    ('f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20','David Raya', 'GOALKEEPER', '1995-09-15'),
    ('a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21','Christian Pulisic', 'FORWARD', '1998-09-18'),
    ('a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21','Weston McKennie', 'FORWARD', '1998-08-28'),
    ('a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21','Matt Turner', 'GOALKEEPER', '1994-06-24'),
    ('c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23','Antoine Semenyo', 'FORWARD', '2000-01-07'),
    ('c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23','Iñaki Williams', 'FORWARD', '1994-06-15'),
    ('c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23','Benjamin Asare', 'GOALKEEPER', '1992-07-13'),
    ('f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26','Mehdi Taremi', 'FORWARD', '1992-07-18'),
    ('f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26','Sardar Azmoun', 'FORWARD', '1995-01-01'),
    ('f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26','Alireza Beiranvand', 'GOALKEEPER', '1992-09-21'),
    ('b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28','Yazan Al-Naimat', 'FORWARD', '1999-06-04'),
    ('b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28','Ali Olwan', 'FORWARD', '2000-03-26'),
    ('b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28','Yazeed Abdulaila', 'GOALKEEPER', '1993-01-08'),
    ('c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29','Achraf Hakimi', 'FORWARD', '1998-11-04'),
    ('c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29','Brahim Díaz', 'FORWARD', '1999-08-03'),
    ('c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29','Yassine Bounou', 'GOALKEEPER', '1991-04-05'),
    ('d3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30','Gilberto Mora', 'FORWARD', '2008-10-14'),
    ('d3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30','Raúl Giménez', 'FORWARD', '1991-05-05'),
    ('d3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30','Guillermo Ochoa', 'GOALKEEPER', '1985-07-13'),
    ('e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31','Erling Haaland', 'FORWARD', '2000-07-21'),
    ('e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31','Martin Ødegaard', 'FORWARD', '1998-12-17'),
    ('e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31','Alexander Sørloth', 'FORWARD', '1995-12-05'),
    ('e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31','Ørjan Nyland', 'GOALKEEPER', '1990-09-10'),
    ('a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33','Virgin van Dijk', 'FORWARD', '1991-07-08'),
    ('a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33','Memphis Depay', 'FORWARD', '1994-02-13'),
    ('a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33','Jeremie Frimpong', 'FORWARD', '2000-12-10'),
    ('a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33','Bart Verbruggen', 'GOALKEEPER', '2002-08-18'),
    ('b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34','Fidel Escobar', 'FORWARD', '1995-01-09'),
    ('b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34','Michael Murillo', 'FORWARD', '1996-02-11'),
    ('b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34','Orlando Mosquera', 'GOALKEEPER', '1994-12-25'),
    ('c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35','Julio Enciso', 'FORWARD', '2004-01-23'),
    ('c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35','Miguel Almirón', 'FORWARD', '1994-02-10'),
    ('c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35','Orlando Gill', 'GOALKEEPER', '2000-06-11'),
    ('d7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36','Cristiano Ronaldo', 'FORWARD', '1985-02-05'),
    ('d7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36','Vitinha', 'FORWARD', '2000-02-13'),
    ('d7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36','Diogo Costa', 'GOALKEEPER', '1999-09-19'),
    ('e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37','Sadio Mané', 'FORWARD', '1992-04-10'),
    ('e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37','Pape Gueye', 'FORWARD', '1999-01-24'),
    ('e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37','Édouard Mendy', 'GOALKEEPER', '1992-03-01'),
    ('f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38','Lyle Foster', 'FORWARD', '2000-09-03'),
    ('f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38','Mbekezeli Mbokazi', 'FORWARD', '2005-09-19'),
    ('f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38','Ronwen Williams', 'GOALKEEPER', '1992-01-21'),
    ('a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39','Granit Xhaka', 'FORWARD', '1992-09-27'),
    ('a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39','Breel Embolo', 'FORWARD', '1997-02-14'),
    ('a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39','Gregor Kobel', 'GOALKEEPER', '1997-12-06'),
    ('c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41','Federico Valverde', 'FORWARD', '1998-07-22'),
    ('c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41','Ronald Araújo', 'FORWARD', '1999-03-07'),
    ('c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41','Sergio Rochet', 'GOALKEEPER', '1993-03-23'),
    ('d9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42','Eldor Shomurodov', 'FORWARD', '1995-06-29'),
    ('d9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42','Rustam Ashurmatov', 'FORWARD', '1996-07-07'),
    ('d9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42','Botirali Ergashev', 'GOALKEEPER', '1988-03-19'),
    ('8f251495-81ba-4724-b575-f7ebecf213c4','Chancel Mbemba', 'FORWARD', '1994-08-08'),
    ('8f251495-81ba-4724-b575-f7ebecf213c4','Cédric Bakambu', 'FORWARD', '1991-04-11'),
    ('8f251495-81ba-4724-b575-f7ebecf213c4','Lionel Mpasi-Nzau', 'GOALKEEPER', '1994-08-01'),
    ('da0e5c75-ba1c-4090-bbba-ad57d0e3b153','Aymen Hussein', 'FORWARD', '1996-03-22'),
    ('da0e5c75-ba1c-4090-bbba-ad57d0e3b153','Zidane Iqbal', 'FORWARD', '2003-04-27'),
    ('da0e5c75-ba1c-4090-bbba-ad57d0e3b153','Jalal Hassan', 'GOALKEEPER', '1991-05-18'),
    ('07782a28-4f6d-4037-86b8-ccff4c2de218','Arda Güler', 'FORWARD', '2005-02-25'),
    ('07782a28-4f6d-4037-86b8-ccff4c2de218','Kenan Yıldız', 'FORWARD', '2005-05-04'),
    ('07782a28-4f6d-4037-86b8-ccff4c2de218','Uğurcan Çakır', 'GOALKEEPER', '1996-04-05'),
    ('219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4','Patrik Schick', 'FORWARD', '1996-01-24'),
    ('219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4','Tomáš Souček', 'FORWARD', '1995-02-27'),
    ('219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4','Vítězslav Jaroš', 'GOALKEEPER', '2001-07-23')
ON CONFLICT (id) DO NOTHING;

-- Create matches table
CREATE TABLE IF NOT EXISTS matches (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      code VARCHAR(10) NOT NULL,
      tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
      home_team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
      away_team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
      home_quota FLOAT NOT NULL DEFAULT 1,
      draw_quota FLOAT NOT NULL DEFAULT 1,
      away_quota FLOAT NOT NULL DEFAULT 1,
      status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
      substatus VARCHAR(20) DEFAULT NULL,
      started_at TIMESTAMP NOT NULL,
      finished_at TIMESTAMP DEFAULT NULL,
      home_goals INT DEFAULT NULL,
      away_goals INT DEFAULT NULL,
      home_penalties INT DEFAULT NULL,
      away_penalties INT DEFAULT NULL,
      has_multiplier BOOLEAN NOT NULL DEFAULT FALSE,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_matches_code ON matches(tournament_id, code) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE matches IS 'Matches table';
COMMENT ON COLUMN matches.id IS 'Unique identifier for the match';
COMMENT ON COLUMN matches.code IS 'Unique identifier for the match within the tournament';
COMMENT ON COLUMN matches.tournament_id IS 'Reference to the tournament';
COMMENT ON COLUMN matches.home_team_id IS 'Home team identifier';
COMMENT ON COLUMN matches.away_team_id IS 'Away team identifier';
COMMENT ON COLUMN matches.home_quota IS 'The quota for a home team win';
COMMENT ON COLUMN matches.draw_quota IS 'The quota for a draw';
COMMENT ON COLUMN matches.away_quota IS 'The quota for an away team win';
COMMENT ON COLUMN matches.status IS 'Status of the match (can be either NOT_STARTED, IN_PROGRESS or FINISHED)';
COMMENT ON COLUMN matches.substatus IS 'Substatus of the in-progress match (can be either HT, FT or the minute of the game)';
COMMENT ON COLUMN matches.started_at IS 'Timestamp when the match started';
COMMENT ON COLUMN matches.finished_at IS 'Timestamp when the match finished';
COMMENT ON COLUMN matches.home_goals IS 'Amount of goals scored by the home team';
COMMENT ON COLUMN matches.away_goals IS 'Amount of goals scored by the away team';
COMMENT ON COLUMN matches.home_penalties IS 'Amount of penalties scored by the home team';
COMMENT ON COLUMN matches.away_penalties IS 'Amount of penalties scored by the away team';
COMMENT ON COLUMN matches.has_multiplier IS 'Flag indicating if a match has a multiplier';
COMMENT ON COLUMN matches.created_at IS 'Timestamp when the match was created';
COMMENT ON COLUMN matches.updated_at IS 'Timestamp when the match was last updated';
COMMENT ON COLUMN matches.deleted_at IS 'Timestamp when the match was deleted';

-- Seed default matches (using Bet365 quotas)
INSERT INTO matches (id, tournament_id, code, home_team_id, away_team_id, started_at, has_multiplier, home_quota, draw_quota, away_quota) VALUES
    ('6a7c9c74-9a3d-4b9e-9a45-0a5dce3a8f3a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '1', 'd3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30', 'f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38', '2026-06-11 13:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(1.45) as numeric), 2), 1+2*round(cast(log(4.10) as numeric), 2), 1+2*round(cast(log(5.75) as numeric), 2)),
    ('0c3d4b9f-cc8b-4c6f-8a54-0c1d9f3c3c41', '28652183-a2d6-4f33-a624-0d24645ce3cd', '2', 'e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13', '219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4', '2026-06-11 20:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(2.55) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(2.60) as numeric), 2)),
    ('0dfe0d4b-80fa-41a8-9e52-7c2b66a67b12', '28652183-a2d6-4f33-a624-0d24645ce3cd', '3', '5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10', 'fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', '2026-06-12 15:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(2.00) as numeric), 2), 1+2*round(cast(log(3.60) as numeric), 2), 1+2*round(cast(log(3.10) as numeric), 2)),
    ('c2d7a8a0-9e5c-4b88-8b89-5d5c1d6d5e6a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '4', 'a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21', 'c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35', '2026-06-12 18:00:00 -07:00'::timestamptz, true, 1+2*round(cast(log(1.85) as numeric), 2), 1+2*round(cast(log(3.50) as numeric), 2), 1+2*round(cast(log(3.80) as numeric), 2)),
    ('c4c0b33f-6f37-4d2b-9c7a-8e4a7c1b9eaa', '28652183-a2d6-4f33-a624-0d24645ce3cd', '5', 'd1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', 'e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19', '2026-06-13 21:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(6.25) as numeric), 2), 1+2*round(cast(log(4.75) as numeric), 2), 1+2*round(cast(log(1.38) as numeric), 2)),
    ('7b6c4a37-12db-4e59-8d64-0e2b1f5d1a8c', '28652183-a2d6-4f33-a624-0d24645ce3cd', '6', '3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05', '07782a28-4f6d-4037-86b8-ccff4c2de218', '2026-06-13 21:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(4.50) as numeric), 2), 1+2*round(cast(log(3.75) as numeric), 2), 1+2*round(cast(log(1.65) as numeric), 2)),
    ('94a8b0d3-95f7-4b4a-8d8a-6e2c3a3a3a2c', '28652183-a2d6-4f33-a624-0d24645ce3cd', '7', '2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07', 'c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29', '2026-06-13 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.50) as numeric), 2), 1+2*round(cast(log(3.90) as numeric), 2), 1+2*round(cast(log(5.50) as numeric), 2)),
    ('3d6a9bcb-b2e0-4c4b-88d1-b5c6f4a6c3b9', '28652183-a2d6-4f33-a624-0d24645ce3cd', '8', 'c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11', 'a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39', '2026-06-13 15:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(8.00) as numeric), 2), 1+2*round(cast(log(5.00) as numeric), 2), 1+2*round(cast(log(1.30) as numeric), 2)),
    ('cfa82b2b-6b3d-4c7c-84f7-b8c6e0f7e3a4', '28652183-a2d6-4f33-a624-0d24645ce3cd', '9', '6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01', 'b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16', '2026-06-14 12:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.02) as numeric), 2), 1+2*round(cast(log(21.00) as numeric), 2), 1+2*round(cast(log(51.0) as numeric), 2)),
    ('8e0e35b3-45c5-44e8-a8c0-f3b9f8c7c87c', '28652183-a2d6-4f33-a624-0d24645ce3cd', '10', 'f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', 'c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17', '2026-06-14 19:00:00 -04:00'::timestamptz, true, 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(3.25) as numeric), 2), 1+2*round(cast(log(2.15) as numeric), 2)),
    ('da8e1f1a-1a2b-4a5d-bb86-8c7e1a9e9b61', '28652183-a2d6-4f33-a624-0d24645ce3cd', '11', 'a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33', 'a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', '2026-06-14 15:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.90) as numeric), 2), 1+2*round(cast(log(3.60) as numeric), 2), 1+2*round(cast(log(3.50) as numeric), 2)),
    ('2f8a2e6c-67a1-45c6-bfa8-6a3d9f0b3c7e', '28652183-a2d6-4f33-a624-0d24645ce3cd', '12', '8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', 'b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', '2026-06-14 20:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(1.83) as numeric), 2), 1+2*round(cast(log(3.50) as numeric), 2), 1+2*round(cast(log(3.80) as numeric), 2)),
    ('c1c84e1d-03a7-4c3e-9e3b-4a5e9c8f3d7a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '13', 'b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02', 'c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41', '2026-06-15 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(5.00) as numeric), 2), 1+2*round(cast(log(3.80) as numeric), 2), 1+2*round(cast(log(1.57) as numeric), 2)),
    ('1e5a2f9c-7d45-49a1-8c5b-9f1d2c3b4a5e', '28652183-a2d6-4f33-a624-0d24645ce3cd', '14', 'f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20', '4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09', '2026-06-15 12:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.05) as numeric), 2), 1+2*round(cast(log(17.0) as numeric), 2), 1+2*round(cast(log(34.0) as numeric), 2)),
    ('6f7a1b2c-9d4e-4c8a-9e7b-2a1c3d4e5f6a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '15', 'f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26', 'f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', '2026-06-15 18:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.66) as numeric), 2), 1+2*round(cast(log(3.60) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2)),
    ('2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d', '28652183-a2d6-4f33-a624-0d24645ce3cd', '16', '8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'd5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18', '2026-06-15 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.53) as numeric), 2), 1+2*round(cast(log(4.00) as numeric), 2), 1+2*round(cast(log(5.50) as numeric), 2)),
    ('8f7e6d5c-4b3a-49e8-b7c6-d5a4f3e2c1b0', '28652183-a2d6-4f33-a624-0d24645ce3cd', '17', 'b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37', '2026-06-16 05:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.42) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2), 1+2*round(cast(log(6.25) as numeric), 2)),
    ('9a8b7c6d-5e4f-4d3c-b2a1-0f9e8d7c6b5a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '18', 'da0e5c75-ba1c-4090-bbba-ad57d0e3b153', 'e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31', '2026-06-16 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(7.00) as numeric), 2), 1+2*round(cast(log(5.00) as numeric), 2), 1+2*round(cast(log(1.30) as numeric), 2)),
    ('0a1b2c3d-4e5f-48a9-b7c6-d5e4f3a2b1c0', '28652183-a2d6-4f33-a624-0d24645ce3cd', '19', '9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04', '1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03', '2026-06-16 20:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.38) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2), 1+2*round(cast(log(7.00) as numeric), 2)),
    ('abcdef12-3456-4abc-8def-1234567890ab', '28652183-a2d6-4f33-a624-0d24645ce3cd', '20', '7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06', 'b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28', '2026-06-16 21:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.33) as numeric), 2), 1+2*round(cast(log(4.75) as numeric), 2), 1+2*round(cast(log(8.50) as numeric), 2)),
    ('12345678-90ab-4cde-8fab-1234567890cd', '28652183-a2d6-4f33-a624-0d24645ce3cd', '21', 'c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23', 'b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34', '2026-06-17 19:00:00 -04:00'::timestamptz, true, 1+2*round(cast(log(1.90) as numeric), 2), 1+2*round(cast(log(3.40) as numeric), 2), 1+2*round(cast(log(3.50) as numeric), 2)),
    ('87654321-0fed-4cba-9fab-abcdefabcdef', '28652183-a2d6-4f33-a624-0d24645ce3cd', '22', 'e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15', '2026-06-17 15:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.61) as numeric), 2), 1+2*round(cast(log(3.90) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2)),
    ('1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', '28652183-a2d6-4f33-a624-0d24645ce3cd', '23', 'd7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36', '8f251495-81ba-4724-b575-f7ebecf213c4', '2026-06-17 12:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.30) as numeric), 2), 1+2*round(cast(log(4.75) as numeric), 2), 1+2*round(cast(log(9.00) as numeric), 2)),
    ('5d4c3b2a-1f0e-49d8-c7b6-a5f4e3d2c1b0', '28652183-a2d6-4f33-a624-0d24645ce3cd', '24', 'd9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42', 'd7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12', '2026-06-17 20:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(7.00) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2), 1+2*round(cast(log(1.36) as numeric), 2)),
    ('7e8f9a0b-1c2d-4e3f-8a9b-0c1d2e3f4a5b', '28652183-a2d6-4f33-a624-0d24645ce3cd', '25', '219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4', 'f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38', '2026-06-18 12:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.72) as numeric), 2), 1+2*round(cast(log(3.50) as numeric), 2), 1+2*round(cast(log(5.00) as numeric), 2)),
    ('9b8a7c6d-5e4f-4d3c-b2a1-0f9e8d7c6b5b', '28652183-a2d6-4f33-a624-0d24645ce3cd', '26', 'a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39', 'fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', '2026-06-18 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.62) as numeric), 2), 1+2*round(cast(log(3.60) as numeric), 2), 1+2*round(cast(log(5.66) as numeric), 2)),
    ('6c5b4a39-2d1e-4f8a-9b7c-6d5e4f3a2b1c', '28652183-a2d6-4f33-a624-0d24645ce3cd', '27', '5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10', 'c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11', '2026-06-18 15:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.55) as numeric), 2), 1+2*round(cast(log(3.70) as numeric), 2), 1+2*round(cast(log(6.50) as numeric), 2)),
    ('4b3a2918-0f1e-4d3c-b2a1-0f9e8d7c6b5c', '28652183-a2d6-4f33-a624-0d24645ce3cd', '28', 'd3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30', 'e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13', '2026-06-18 19:00:00 -06:00'::timestamptz, true, 1+2*round(cast(log(1.87) as numeric), 2), 1+2*round(cast(log(3.33) as numeric), 2), 1+2*round(cast(log(4.20) as numeric), 2)),
    ('3c2b1a09-8f7e-4d3c-b2a1-0f9e8d7c6b5d', '28652183-a2d6-4f33-a624-0d24645ce3cd', '29', '2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07', 'd1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', '2026-06-19 21:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.05) as numeric), 2), 1+2*round(cast(log(11.0) as numeric), 2), 1+2*round(cast(log(53.0) as numeric), 2)),
    ('2d1c0b9a-8f7e-4d3c-b2a1-0f9e8d7c6b5e', '28652183-a2d6-4f33-a624-0d24645ce3cd', '30', 'e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19', 'c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29', '2026-06-19 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(3.85) as numeric), 2), 1+2*round(cast(log(3.15) as numeric), 2), 1+2*round(cast(log(2.05) as numeric), 2)),
    ('1e0d9c8b-7a6f-4d3c-b2a1-0f9e8d7c6b5f', '28652183-a2d6-4f33-a624-0d24645ce3cd', '31', '07782a28-4f6d-4037-86b8-ccff4c2de218', 'c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35', '2026-06-19 21:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(2.12) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(3.55) as numeric), 2)),
    ('0f9e8d7c-6b5a-4d3c-b2a1-0f9e8d7c6b50', '28652183-a2d6-4f33-a624-0d24645ce3cd', '32', 'a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21', '3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05', '2026-06-19 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.77) as numeric), 2), 1+2*round(cast(log(4.00) as numeric), 2), 1+2*round(cast(log(6.07) as numeric), 2)),
    ('aa1bb2cc-3dd4-4ee5-8ff6-77889900aabb', '28652183-a2d6-4f33-a624-0d24645ce3cd', '33', '6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01', 'f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', '2026-06-20 16:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.53) as numeric), 2), 1+2*round(cast(log(4.00) as numeric), 2), 1+2*round(cast(log(6.00) as numeric), 2)),
    ('bb2cc3dd-4ee5-4ff6-9aa7-889900bbccdd', '28652183-a2d6-4f33-a624-0d24645ce3cd', '34', 'c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17', 'b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16', '2026-06-20 19:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.24) as numeric), 2), 1+2*round(cast(log(5.50) as numeric), 2), 1+2*round(cast(log(13.0) as numeric), 2)),
    ('cc3dd4ee-5ff6-4aa7-abb8-9900ccddee11', '28652183-a2d6-4f33-a624-0d24645ce3cd', '35', 'a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33', '8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', '2026-06-20 12:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.72) as numeric), 2), 1+2*round(cast(log(3.75) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2)),
    ('dd4ee5ff-6aa7-4bb8-bcc9-00ddeeff1122', '28652183-a2d6-4f33-a624-0d24645ce3cd', '36', 'b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', '2026-06-20 22:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(4.75) as numeric), 2), 1+2*round(cast(log(3.30) as numeric), 2), 1+2*round(cast(log(1.81) as numeric), 2)),
    ('ee5ff6aa-7bb8-4cc9-cdda-eeff11223344', '28652183-a2d6-4f33-a624-0d24645ce3cd', '37', 'c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41', '4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09', '2026-06-21 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.44) as numeric), 2), 1+2*round(cast(log(4.30) as numeric), 2), 1+2*round(cast(log(7.25) as numeric), 2)),
    ('ff6aa7bb-8cc9-4dda-deeb-ff1122334455', '28652183-a2d6-4f33-a624-0d24645ce3cd', '38', 'f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20', 'b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02', '2026-06-21 12:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.14) as numeric), 2), 1+2*round(cast(log(7.00) as numeric), 2), 1+2*round(cast(log(22.0) as numeric), 2)),
    ('11223344-5566-4a77-8b88-99aabbccdde0', '28652183-a2d6-4f33-a624-0d24645ce3cd', '39', '8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', 'f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26', '2026-06-21 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.57) as numeric), 2), 1+2*round(cast(log(3.14) as numeric), 2), 1+2*round(cast(log(5.11) as numeric), 2)),
    ('22334455-6677-4b88-9c99-aabbccddeeff', '28652183-a2d6-4f33-a624-0d24645ce3cd', '40', 'f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', 'd5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18', '2026-06-21 18:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(5.33) as numeric), 2), 1+2*round(cast(log(3.65) as numeric), 2), 1+2*round(cast(log(1.68) as numeric), 2)),
    ('33445566-7788-4c99-ad00-bbccddeeff11', '28652183-a2d6-4f33-a624-0d24645ce3cd', '41', 'e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31', 'e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37', '2026-06-22 20:00:00 -04:00'::timestamptz, true, 1+2*round(cast(log(2.05) as numeric), 2), 1+2*round(cast(log(3.30) as numeric), 2), 1+2*round(cast(log(3.66) as numeric), 2)),
    ('44556677-8899-4daa-be11-ccddeeff1122', '28652183-a2d6-4f33-a624-0d24645ce3cd', '42', 'b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', 'da0e5c75-ba1c-4090-bbba-ad57d0e3b153', '2026-06-22 17:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.13) as numeric), 2), 1+2*round(cast(log(6.75) as numeric), 2), 1+2*round(cast(log(26.0) as numeric), 2)),
    ('55667788-99aa-4ebb-cf22-ddeeff112233', '28652183-a2d6-4f33-a624-0d24645ce3cd', '43', '9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04', '7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06', '2026-06-22 12:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.53) as numeric), 2), 1+2*round(cast(log(4.00) as numeric), 2), 1+2*round(cast(log(6.25) as numeric), 2)),
    ('66778899-aabb-4fcc-d033-eeff11223344', '28652183-a2d6-4f33-a624-0d24645ce3cd', '44', 'b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28', '1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03', '2026-06-22 21:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(7.00) as numeric), 2), 1+2*round(cast(log(3.66) as numeric), 2), 1+2*round(cast(log(1.53) as numeric), 2)),
    ('778899aa-bbcc-40dd-e144-ff1122334455', '28652183-a2d6-4f33-a624-0d24645ce3cd', '45', 'e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', 'c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23', '2026-06-23 16:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.33) as numeric), 2), 1+2*round(cast(log(4.75) as numeric), 2), 1+2*round(cast(log(9.00) as numeric), 2)),
    ('8899aabb-ccdd-41ee-f255-001122334455', '28652183-a2d6-4f33-a624-0d24645ce3cd', '46', 'b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34', 'a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15', '2026-06-23 19:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(8.50) as numeric), 2), 1+2*round(cast(log(4.40) as numeric), 2), 1+2*round(cast(log(1.38) as numeric), 2)),
    ('99aabbcc-ddee-42ff-0366-112233445566', '28652183-a2d6-4f33-a624-0d24645ce3cd', '47', 'd7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36', 'd9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42', '2026-06-23 12:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(1.17) as numeric), 2), 1+2*round(cast(log(6.50) as numeric), 2), 1+2*round(cast(log(17.0) as numeric), 2)),
    ('aabbccdd-eeff-4300-1477-223344556677', '28652183-a2d6-4f33-a624-0d24645ce3cd', '48', 'd7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12', '8f251495-81ba-4724-b575-f7ebecf213c4', '2026-06-23 20:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(1.57) as numeric), 2), 1+2*round(cast(log(4.05) as numeric), 2), 1+2*round(cast(log(6.95) as numeric), 2)),
    ('bbccddee-ff00-4411-2588-334455667788', '28652183-a2d6-4f33-a624-0d24645ce3cd', '49', 'e7c3a1b5-9d2f-4c8a-9e6b-2f3a1c7d5b19', '2c5f8a1b-9d3e-4c7a-8b6f-1e2a9c3d4f07', '2026-06-24 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(6.50) as numeric), 2), 1+2*round(cast(log(4.20) as numeric), 2), 1+2*round(cast(log(1.45) as numeric), 2)),
    ('ccddee00-1122-4522-3699-445566778899', '28652183-a2d6-4f33-a624-0d24645ce3cd', '50', 'c7a3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b29', 'd1c5e7b3-2f9a-4a6c-8e1d-3c7a5f2b9e24', '2026-06-24 18:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.30) as numeric), 2), 1+2*round(cast(log(5.00) as numeric), 2), 1+2*round(cast(log(9.00) as numeric), 2)),
    ('ddee0011-2233-4633-47aa-5566778899aa', '28652183-a2d6-4f33-a624-0d24645ce3cd', '51', 'a3c1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b39', '5a1c9e3b-7d2f-4a6c-8b9e-2f3d7a1c4b10', '2026-06-24 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.95) as numeric), 2), 1+2*round(cast(log(3.30) as numeric), 2), 1+2*round(cast(log(3.90) as numeric), 2)),
    ('ee001122-3344-4744-58bb-66778899aabb', '28652183-a2d6-4f33-a624-0d24645ce3cd', '52', 'fd93fbe8-8ea8-4cbf-a39f-f060891f63f1', 'c3e7b1a9-5f2d-4c8a-9e6b-1a2f3d7c5b11', '2026-06-24 12:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(1.85) as numeric), 2), 1+2*round(cast(log(3.40) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2)),
    ('00112233-4455-4855-69cc-778899aabbcc', '28652183-a2d6-4f33-a624-0d24645ce3cd', '53', '219c87e8-15ab-4ca1-b7f4-c5aed3dc33f4', 'd3c1e7b5-2f9a-4a6c-8e1d-3c7a5f2b9e30', '2026-06-24 19:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(3.00) as numeric), 2), 1+2*round(cast(log(3.25) as numeric), 2), 1+2*round(cast(log(2.40) as numeric), 2)),
    ('11223344-5566-4966-7add-8899aabbccdd', '28652183-a2d6-4f33-a624-0d24645ce3cd', '54', 'f5a1c7e3-2f9d-4a6c-8e1b-3d7a5c2f9e38', 'e1c5a7d3-2f9b-4c8a-9e6d-3a1b2c7f5d13', '2026-06-24 19:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(2.30) as numeric), 2)),
    ('22334455-6677-4a77-8bee-99aabbccddee', '28652183-a2d6-4f33-a624-0d24645ce3cd', '55', 'b7e3c1a5-2f9d-4a6c-8e1b-3d7a5c2f9e16', 'f9b3e1c7-5a2d-4a6c-8e1f-7c3b2a9d5e14', '2026-06-25 16:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(5.50) as numeric), 2), 1+2*round(cast(log(3.80) as numeric), 2), 1+2*round(cast(log(1.60) as numeric), 2)),
    ('33445566-7788-4b88-9cff-aabbccddeeff', '28652183-a2d6-4f33-a624-0d24645ce3cd', '56', 'c1a5e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b17', '6f1c5f6e-9c9e-4f3b-8d8e-2b5e2a6a1c01', '2026-06-25 16:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(4.20) as numeric), 2), 1+2*round(cast(log(3.60) as numeric), 2), 1+2*round(cast(log(1.75) as numeric), 2)),
    ('44556677-8899-4c99-ad10-bbccddeeff00', '28652183-a2d6-4f33-a624-0d24645ce3cd', '57', 'a7c3e1b5-9d2f-4c8a-9e6b-2f3a1c7d5b27', '8c2ac206-1a3c-4ca6-89e1-5ff86c15f9ac', '2026-06-25 18:00:00 -05:00'::timestamptz, true, 1+2*round(cast(log(2.50) as numeric), 2), 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(2.70) as numeric), 2)),
    ('55667788-99aa-4daa-be21-ccddeeff0011', '28652183-a2d6-4f33-a624-0d24645ce3cd', '58', 'b9e3a1c7-5f2d-4c8a-9e6b-1a2f3d7c5b40', 'a1e7c3b5-9d2f-4c8a-9e6b-2f3a1c7d5b33', '2026-06-25 18:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(5.80) as numeric), 2), 1+2*round(cast(log(3.90) as numeric), 2), 1+2*round(cast(log(1.55) as numeric), 2)),
    ('66778899-aabb-4ebb-cf32-ddeeff001122', '28652183-a2d6-4f33-a624-0d24645ce3cd', '59', '07782a28-4f6d-4037-86b8-ccff4c2de218', 'a9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b21', '2026-06-25 19:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(2.60) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(2.60) as numeric), 2)),
    ('778899aa-bbcc-4fcc-d043-eeff00112233', '28652183-a2d6-4f33-a624-0d24645ce3cd', '60', 'c5e1a7b3-9d2f-4c8a-9e6b-2f3a1c7d5b35', '3e7b1c9d-6f2a-4d8c-8b1e-5c9a2f7d3b05', '2026-06-25 19:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(2.10) as numeric), 2), 1+2*round(cast(log(3.25) as numeric), 2), 1+2*round(cast(log(3.40) as numeric), 2)),
    ('8899aabb-ccdd-40dd-e154-ff0011223344', '28652183-a2d6-4f33-a624-0d24645ce3cd', '61', 'e5a1c7b3-9d2f-4c8a-9e6b-2f3a1c7d5b31', 'b1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e22', '2026-06-26 15:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(5.00) as numeric), 2), 1+2*round(cast(log(3.80) as numeric), 2), 1+2*round(cast(log(1.75) as numeric), 2)),
    ('99aabbcc-ddee-41ee-f265-000112233445', '28652183-a2d6-4f33-a624-0d24645ce3cd', '62', 'e3c1a7b5-9d2f-4c8a-9e6b-2f3a1c7d5b37', 'da0e5c75-ba1c-4090-bbba-ad57d0e3b153', '2026-06-26 15:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.60) as numeric), 2), 1+2*round(cast(log(3.70) as numeric), 2), 1+2*round(cast(log(5.50) as numeric), 2)),
    ('aabbccdd-eeff-42ff-0376-111223344556', '28652183-a2d6-4f33-a624-0d24645ce3cd', '63', 'd5b1c7e3-2f9a-4a6c-8e1d-3c7a5f2b9e18', 'f3a1c5e7-2f9d-4a6c-8e1b-3d7a5c2f9e26', '2026-06-26 20:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(2.30) as numeric), 2), 1+2*round(cast(log(3.00) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2)),
    ('bbccddee-ff00-4300-1487-222334455667', '28652183-a2d6-4f33-a624-0d24645ce3cd', '64', 'f7c3a1e5-2f9d-4a6c-8e1b-3d7a5c2f9e32', '8b3e1c7a-2d9f-4a6c-9e5b-3f7a1c2d8b08', '2026-06-26 20:00:00 -07:00'::timestamptz, false, 1+2*round(cast(log(12.0) as numeric), 2), 1+2*round(cast(log(6.00) as numeric), 2), 1+2*round(cast(log(1.20) as numeric), 2)),
    ('ccddee00-1122-4411-2598-333445566778', '28652183-a2d6-4f33-a624-0d24645ce3cd', '65', '4d7a2c1e-8f3b-4c9a-9e1d-6b2f7a3c5e09', 'b2c9c3e7-7f7e-4a5a-9f2b-3c1d9a4e8b02', '2026-06-26 18:00:00 -06:00'::timestamptz, false, 1+2*round(cast(log(2.70) as numeric), 2), 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(2.60) as numeric), 2)),
    ('ddee0011-2233-4522-36a9-444556677889', '28652183-a2d6-4f33-a624-0d24645ce3cd', '66', 'c3a1e7b5-9d2f-4c8a-9e6b-2f3a1c7d5b41', 'f1e7c3a5-2f9d-4a6c-8e1b-3d7a5c2f9e20', '2026-06-26 18:00:00 -06:00'::timestamptz, true, 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(2.20) as numeric), 2)),
    ('ee001122-3344-4633-47ba-55566778899a', '28652183-a2d6-4f33-a624-0d24645ce3cd', '67', 'b3c1e7a5-2f9d-4a6c-8e1b-3d7a5c2f9e34', 'e9b3c1a7-5f2d-4c8a-9e6b-1a2f3d7c5b25', '2026-06-27 17:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(9.00) as numeric), 2), 1+2*round(cast(log(4.80) as numeric), 2), 1+2*round(cast(log(1.30) as numeric), 2)),
    ('00112233-4455-4744-58cb-666778899aab', '28652183-a2d6-4f33-a624-0d24645ce3cd', '68', 'a5c1e7b3-9d2f-4c8a-9e6b-2f3a1c7d5b15', 'c9e3a1b5-5f2d-4c8a-9e6b-1a2f3d7c5b23', '2026-06-27 17:00:00 -04:00'::timestamptz, false, 1+2*round(cast(log(1.85) as numeric), 2), 1+2*round(cast(log(3.30) as numeric), 2), 1+2*round(cast(log(4.50) as numeric), 2)),
    ('11223344-5566-4855-69dc-7778899aabbc', '28652183-a2d6-4f33-a624-0d24645ce3cd', '69', '1a4d2b6c-5e7f-4c8a-9d1e-6b3f2a7c9d03', '7a9d3c1e-5b2f-4a6c-9e8d-2f1b3c7a6d06', '2026-06-27 21:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(15.0) as numeric), 2), 1+2*round(cast(log(6.50) as numeric), 2), 1+2*round(cast(log(1.15) as numeric), 2)),
    ('22334455-6677-4966-7aed-88899aabbccd', '28652183-a2d6-4f33-a624-0d24645ce3cd', '70', 'b5e1c7a3-2f9d-4a6c-8e1b-3d7a5c2f9e28', '9c2e1f4b-8a7d-4b6c-9e3f-1a2b5c7d8e04', '2026-06-27 21:00:00 -05:00'::timestamptz, false, 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(2.25) as numeric), 2)),
    ('33445566-7788-4a77-8bfe-999aabbccdde', '28652183-a2d6-4f33-a624-0d24645ce3cd', '71', 'd7a2c5e1-9b3f-4a6c-8e1d-2c7a3f5b9e12', 'd7a1c5e3-2f9a-4a6c-8e1d-3c7a5f2b9e36', '2026-06-27 19:30:00 -04:00'::timestamptz, false, 1+2*round(cast(log(3.20) as numeric), 2), 1+2*round(cast(log(3.25) as numeric), 2), 1+2*round(cast(log(2.15) as numeric), 2)),
    ('44556677-8899-4b88-9c0f-aaabbccddeef', '28652183-a2d6-4f33-a624-0d24645ce3cd', '72', '8f251495-81ba-4724-b575-f7ebecf213c4', 'd9b3c1e7-5f2d-4c8a-9e6b-1a2f3d7c5b42', '2026-06-27 19:30:00 -04:00'::timestamptz, true, 1+2*round(cast(log(3.00) as numeric), 2), 1+2*round(cast(log(3.10) as numeric), 2), 1+2*round(cast(log(2.40) as numeric), 2))
ON CONFLICT (id) DO NOTHING;

-- Testing matches
-- update matches set status='FINISHED', started_at=(current_timestamp - interval '1 days 20 hours'), finished_at=(current_timestamp - interval '1 days 18 hours'), home_goals=2, away_goals=1 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='1';
-- update matches set status='FINISHED', started_at=(current_timestamp - interval '1 days 17 hours'), finished_at=(current_timestamp - interval '1 days 15 hours'), home_goals=5, away_goals=0 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='2';
-- update matches set status='FINISHED', started_at=(current_timestamp - interval '21 hours'), finished_at=(current_timestamp - interval '19 hours'), home_goals=0, away_goals=0 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='3';
-- update matches set status='FINISHED', started_at=(current_timestamp - interval '18 hours'), finished_at=(current_timestamp - interval '16 hours'), home_goals=1, away_goals=2 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='4';
-- update matches set status='IN_PROGRESS', substatus='HT', started_at=(current_timestamp - interval '1 hours 30 minutes'), home_goals=3, away_goals=1 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='7';
-- update matches set status='IN_PROGRESS', substatus='45+7''', started_at=(current_timestamp - interval '30 minutes'), home_goals=0, away_goals=2 where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='8';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '1 days') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='5';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '1 days 2 hours') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='6';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '2 days 1 hours') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='9';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '2 days 3 hours') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='11';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '3 days 0 hours') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='10';
-- update matches set status='NOT_STARTED', started_at=(current_timestamp + interval '3 days 2 hours') where tournament_id='28652183-a2d6-4f33-a624-0d24645ce3cd' and code='12';

-- Create matches-predictions table
CREATE TABLE IF NOT EXISTS match_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    home_goals INT,
    away_goals INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_match_predictions_uniqueness ON match_predictions(user_id, group_id, match_id) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE match_predictions IS 'Matches predictions table';
COMMENT ON COLUMN match_predictions.id IS 'Unique identifier for the match';
COMMENT ON COLUMN match_predictions.user_id IS 'Reference to the user';
COMMENT ON COLUMN match_predictions.group_id IS 'Reference to the group';
COMMENT ON COLUMN match_predictions.match_id IS 'Reference to the match';
COMMENT ON COLUMN match_predictions.home_goals IS 'The home goals predicted';
COMMENT ON COLUMN match_predictions.away_goals IS 'The away goals predicted';
COMMENT ON COLUMN match_predictions.status IS 'Status of the prediction (can be either BONUS, CORRECT, PARTIAL, INCORRECT, PENDING or MISSING)';
COMMENT ON COLUMN match_predictions.created_at IS 'Timestamp when the prediction was created';
COMMENT ON COLUMN match_predictions.updated_at IS 'Timestamp when the prediction was updated';
COMMENT ON COLUMN match_predictions.deleted_at IS 'Timestamp when the prediction was deleted';

-- Adding testing predictions
INSERT INTO match_predictions (user_id, group_id, match_id, home_goals, away_goals, status)
SELECT gu.user_id, gu.group_id, m.id, floor(random()*5)::int, floor(random()*5)::int, 'PENDING'
FROM group_users gu
     JOIN matches m ON m.code::int BETWEEN 1 AND 72;

-- Create awards-predictions table
CREATE TABLE IF NOT EXISTS award_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    award_type VARCHAR(20) NOT NULL,
    awarded_team_id UUID NULL REFERENCES teams(id) ON DELETE CASCADE,
    awarded_player_id UUID NULL REFERENCES players(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_award_predictions_uniqueness ON award_predictions(user_id, award_type, awarded_team_id, awarded_player_id) WHERE deleted_at IS NULL;

-- Add comments to table and columns
COMMENT ON TABLE award_predictions IS 'Awards predictions table';
COMMENT ON COLUMN award_predictions.id IS 'Unique identifier for the match';
COMMENT ON COLUMN award_predictions.user_id IS 'Reference to the user';
COMMENT ON COLUMN award_predictions.award_type IS 'Type of the award, depending on the tournament';
COMMENT ON COLUMN award_predictions.awarded_team_id IS 'Reference to the team awarded (optional)';
COMMENT ON COLUMN award_predictions.awarded_player_id IS 'Reference to the player awarded (optional)';
COMMENT ON COLUMN award_predictions.status IS 'Status of the prediction (can be either BONUS, CORRECT, PARTIAL, INCORRECT, PENDING or MISSING)';
COMMENT ON COLUMN award_predictions.created_at IS 'Timestamp when the prediction was created';
COMMENT ON COLUMN award_predictions.updated_at IS 'Timestamp when the prediction was updated';
COMMENT ON COLUMN award_predictions.deleted_at IS 'Timestamp when the prediction was deleted';

INSERT INTO award_predictions (user_id, group_id, award_type, awarded_team_id, awarded_player_id)
-- 1. CHAMPION (1–2 random teams)
SELECT gu.user_id, gu.group_id, award_type, team_id, NULL
FROM group_users gu
     JOIN LATERAL (
    SELECT 'CHAMPION' AS award_type, teams.id AS team_id
    FROM teams WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 2))::int + 1) ch ON TRUE
UNION ALL
-- 2. TOP_SCORER (1–3 players)
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
     JOIN LATERAL (
    SELECT 'TOP_SCORER' AS award_type, players.id AS player_id
    FROM players WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) ts ON TRUE
UNION ALL
-- 3. BEST_PLAYER (1–3 players)
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
     JOIN LATERAL (
    SELECT 'BEST_PLAYER' AS award_type, players.id AS player_id
    FROM players WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) bp ON TRUE
UNION ALL
-- 4. BEST_GOALKEEPER (1–3 players, filtered)
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
     JOIN LATERAL (
    SELECT 'BEST_GOALKEEPER' AS award_type, p.id AS player_id
    FROM players p WHERE gu.user_id IS NOT NULL AND p.position = 'GOALKEEPER'
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) bg ON TRUE
UNION ALL
-- 5. BEST_YOUNG_PLAYER (1–3 players, filtered)
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
     JOIN LATERAL (
    SELECT 'BEST_YOUNG_PLAYER' AS award_type, p.id AS player_id
    FROM players p WHERE gu.user_id IS NOT NULL AND p.birthdate > DATE '2005-01-01'
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) byp ON TRUE;

DELETE FROM award_predictions ap
    USING (
    SELECT DISTINCT ON (gu.group_id) gu.group_id, gu.user_id, at.award_type
    FROM group_users gu
      JOIN LATERAL (
      SELECT award_type FROM award_predictions ap2
      WHERE ap2.group_id = gu.group_id ORDER BY random() LIMIT 1) at ON TRUE
    ORDER BY gu.group_id, random()
    ) picked
WHERE ap.group_id = picked.group_id
  AND ap.user_id = picked.user_id
  AND ap.award_type = picked.award_type;
