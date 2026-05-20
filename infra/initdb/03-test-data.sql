INSERT INTO users (id, fullname, username, email, password_hash, permissions) VALUES
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'Cristian Raña', 'cris', 'cris@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'SUPERUSER'),
    ('60635292-4a13-43d8-b976-b2e292020deb', 'Lautaro Chamorro', 'chas', 'chas@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4fc682de-233f-4b0f-b4c3-4ee0f5716675', 'Manuel Domínguez', 'manu', 'manu@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('56118705-5d57-4a6d-9f38-46606c78dbd6', 'Federico Cornago', 'corna', 'corna@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('ef7aacbb-e1f8-46eb-bbd9-21dafe041749', 'Jonathan Link', 'jona', 'jona@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('b49d0241-d664-4223-ba33-d448ec050abe', 'Guido Landesman', 'landes', 'landes@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('a2618ce3-03a0-4f81-bb0b-010c0245a65b', 'Gastón Macrini', 'macro', 'macro@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('2b67aaa9-2ecf-4d21-9ce1-e378337b6adb', 'Gastón Añón', 'añon', 'añon@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('b7d358aa-42c0-4b22-9da2-ed292a00ee47', 'Federico Groisman', 'grois', 'grois@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('6baadbd8-dd3e-4926-8de6-e5908f774c4e', 'Rodrigo Díaz', 'rodri', 'rodri@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4cb252f7-9dac-4249-a9a2-b45d5234d384', 'Franco Rapallini', 'fran', 'fran@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('bdbf29ee-cfda-4d02-9928-af93ebd40892', 'Facundo Gandara', 'rifle', 'rifle@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('4a97633c-649b-4315-91a9-f995dc950171', 'Germán Raña', 'germán', 'german@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('dab4229a-e438-4d11-8f29-26320991848f', 'Ariel Canteros', 'ariel', 'ariel@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER'),
    ('23295782-bc35-4d70-892b-37a771620bc7', 'Camila Ivanovich', 'cami', 'cami@gmail.com', '5d7845ac6ee7cfffafc5fe5f35cf666d', 'USER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO groups (id, tournament_id, name, is_private, max_members) VALUES
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'General', FALSE, 50),
    ('7c9e6679-7425-40de-944b-e07fc1f90ae7', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'EPO', TRUE, 25),
    ('b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Baldosa', TRUE, 27),
    ('e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Maldolar', TRUE, 12),
    ('8158a607-97b3-47db-8382-92d878358b9c', '28652183-a2d6-4f33-a624-0d24645ce3cd', 'Familia', TRUE, 20)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_users (user_id, group_id, role) VALUES
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'OWNER'),
    ('c97ec073-c40c-4094-9f9e-b07074188936', '8158a607-97b3-47db-8382-92d878358b9c', 'OWNER'),
    ('60635292-4a13-43d8-b976-b2e292020deb', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'ADMIN'),
    ('4fc682de-233f-4b0f-b4c3-4ee0f5716675', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('56118705-5d57-4a6d-9f38-46606c78dbd6', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('ef7aacbb-e1f8-46eb-bbd9-21dafe041749', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('b49d0241-d664-4223-ba33-d448ec050abe', '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'MEMBER'),
    ('a2618ce3-03a0-4f81-bb0b-010c0245a65b', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'ADMIN'),
    ('2b67aaa9-2ecf-4d21-9ce1-e378337b6adb', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'MEMBER'),
    ('b7d358aa-42c0-4b22-9da2-ed292a00ee47', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'MEMBER'),
    ('6baadbd8-dd3e-4926-8de6-e5908f774c4e', 'e8d7c6b5-a4f3-4e2d-9c1b-0a8f7e6d5c4b', 'MEMBER'),
    ('4cb252f7-9dac-4249-a9a2-b45d5234d384', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'ADMIN'),
    ('bdbf29ee-cfda-4d02-9928-af93ebd40892', 'b5d4c3a2-1e0f-4d9c-8b7a-6f5e4d3c2b1a', 'MEMBER'),
    ('4a97633c-649b-4315-91a9-f995dc950171', '8158a607-97b3-47db-8382-92d878358b9c', 'ADMIN'),
    ('dab4229a-e438-4d11-8f29-26320991848f', '8158a607-97b3-47db-8382-92d878358b9c', 'MEMBER'),
    ('23295782-bc35-4d70-892b-37a771620bc7', '8158a607-97b3-47db-8382-92d878358b9c', 'MEMBER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO match_predictions (user_id, group_id, match_id, home_goals, away_goals, status)
SELECT gu.user_id, gu.group_id, m.id, floor(random()*5)::int, floor(random()*5)::int, 'PENDING'
FROM group_users gu
JOIN matches m ON m.code::int BETWEEN 1 AND 72;

INSERT INTO award_predictions (user_id, group_id, award_type, awarded_team_id, awarded_player_id)
SELECT gu.user_id, gu.group_id, award_type, team_id, NULL
FROM group_users gu
JOIN LATERAL (
    SELECT 'CHAMPION' AS award_type, teams.id AS team_id
    FROM teams WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 2))::int + 1) ch ON TRUE
UNION ALL
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
JOIN LATERAL (
    SELECT 'TOP_SCORER' AS award_type, players.id AS player_id
    FROM players WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) ts ON TRUE
UNION ALL
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
JOIN LATERAL (
    SELECT 'BEST_PLAYER' AS award_type, players.id AS player_id
    FROM players WHERE gu.user_id IS NOT NULL
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) bp ON TRUE
UNION ALL
SELECT gu.user_id, gu.group_id, award_type, NULL, player_id
FROM group_users gu
JOIN LATERAL (
    SELECT 'BEST_GOALKEEPER' AS award_type, p.id AS player_id
    FROM players p WHERE gu.user_id IS NOT NULL AND p.position = 'GOALKEEPER'
    ORDER BY random() LIMIT (floor(random() * 3))::int + 1) bg ON TRUE
UNION ALL
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
