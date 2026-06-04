-- Initialize database schema for Grondona application

-- Automatically update updated_at on row updates
create or replace function set_updated_at()
    returns trigger as $$
begin
    NEW.updated_at = CURRENT_TIMESTAMP;
    return NEW;
end;
$$ language plpgsql;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fullname TEXT NOT NULL,
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    reset_token TEXT DEFAULT NULL,
    permissions TEXT NOT NULL DEFAULT 'USER',
    unique_predictions BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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

-- Create tournaments table
CREATE TABLE IF NOT EXISTS tournaments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED',
    awards JSONB DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_tournaments_name ON tournaments(name) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_tournaments_updated_at
BEFORE UPDATE ON tournaments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Add comments to table and columns
COMMENT ON TABLE tournaments IS 'Tournaments table';
COMMENT ON COLUMN tournaments.id IS 'Unique identifier for the tournament';
COMMENT ON COLUMN tournaments.name IS 'Name of the tournament';
COMMENT ON COLUMN tournaments.status IS 'Status of the tournament (can be either NOT_STARTED, IN_PROGRESS or FINISHED)';
COMMENT ON COLUMN tournaments.awards IS 'Awards of the tournament (populated at the end of it)';
COMMENT ON COLUMN tournaments.created_at IS 'Timestamp when the tournament was created';
COMMENT ON COLUMN tournaments.updated_at IS 'Timestamp when the tournament was last updated';
COMMENT ON COLUMN tournaments.deleted_at IS 'Timestamp when the tournament was deleted';

-- Create groups table
CREATE TABLE IF NOT EXISTS groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    name TEXT NOT NULL UNIQUE,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    max_members INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_name ON groups(tournament_id, name) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_groups_updated_at BEFORE UPDATE ON groups FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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

-- Create group_users table (membership)
CREATE TABLE IF NOT EXISTS group_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MEMBER',
    rank INTEGER DEFAULT NULL,
    points FLOAT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP DEFAULT NULL,
    amount_bonus INTEGER DEFAULT 0,
    amount_correct INTEGER DEFAULT 0,
    amount_partial INTEGER DEFAULT 0,
    last_predictions TEXT[] NOT NULL DEFAULT '{}',
    predicted_awards JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_group_users_user_id ON group_users(user_id);
CREATE INDEX IF NOT EXISTS idx_group_users_group_id ON group_users(group_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_group_users_uniqueness ON group_users(user_id, group_id) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_group_users_updated_at BEFORE UPDATE ON group_users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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

-- Create teams table
CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    name_es TEXT NOT NULL,
    name_en TEXT NOT NULL,
    icon TEXT DEFAULT 'https://flagicons.lipis.dev/flags/4x3/xx.svg',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_teams_code ON teams(code, tournament_id) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_teams_updated_at BEFORE UPDATE ON teams FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Add comments to table and columns
COMMENT ON TABLE teams IS 'Teams table';
COMMENT ON COLUMN teams.id IS 'Unique identifier for the team';
COMMENT ON COLUMN teams.tournament_id IS 'Reference to the tournament';
COMMENT ON COLUMN teams.name_es IS 'Name of the team (in Spanish)';
COMMENT ON COLUMN teams.name_en IS 'Name of the team (in English)';
COMMENT ON COLUMN teams.code IS 'FIFA code of the team';
COMMENT ON COLUMN teams.icon IS 'URL with the team icon';
COMMENT ON COLUMN teams.created_at IS 'Timestamp when the team was created';
COMMENT ON COLUMN teams.updated_at IS 'Timestamp when the team was last updated';
COMMENT ON COLUMN teams.deleted_at IS 'Timestamp when the team was deleted';

-- Create teams table
CREATE TABLE IF NOT EXISTS players (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      position TEXT NOT NULL,
      birthdate DATE NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE INDEX IF NOT EXISTS idx_players_team ON players(name, team_id) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_players_updated_at BEFORE UPDATE ON players FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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

-- Create matches table
CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL,
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    home_team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    away_team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    stage TEXT NOT NULL,
    "group" TEXT DEFAULT NULL,
    home_quota FLOAT NOT NULL DEFAULT 1,
    draw_quota FLOAT NOT NULL DEFAULT 1,
    away_quota FLOAT NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED',
    substatus TEXT DEFAULT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz DEFAULT NULL,
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

-- Create trigger for the updated_at field
CREATE TRIGGER trg_matches_updated_at BEFORE UPDATE ON matches FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
COMMENT ON COLUMN matches.stage IS 'Stage of the tournament';
COMMENT ON COLUMN matches.group IS 'Optional value for group stage, indicating the group.';
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

-- Create matches-predictions table
CREATE TABLE IF NOT EXISTS match_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    home_goals INT,
    away_goals INT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_match_predictions_uniqueness ON match_predictions(user_id, group_id, match_id) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_match_predictions_updated_at BEFORE UPDATE ON match_predictions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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

-- Create awards-predictions table
CREATE TABLE IF NOT EXISTS award_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    award_type TEXT NOT NULL,
    awarded_team_id UUID NULL REFERENCES teams(id) ON DELETE CASCADE,
    awarded_player_id UUID NULL REFERENCES players(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Create indexes for uniqueness and better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_award_predictions_uniqueness ON award_predictions(user_id, award_type, awarded_team_id, awarded_player_id) WHERE deleted_at IS NULL;

-- Create trigger for the updated_at field
CREATE TRIGGER trg_award_predictions_updated_at BEFORE UPDATE ON award_predictions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
