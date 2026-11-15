ALTER TABLE chats
    ADD COLUMN message_id VARCHAR(36);

UPDATE chats
SET message_id = gen_random_uuid()::text
WHERE message_id IS NULL;

ALTER TABLE chats
    ALTER COLUMN message_id SET NOT NULL,
    ADD CONSTRAINT uk_chats_message_id UNIQUE (message_id);

ALTER TABLE reactions
    ADD COLUMN message_id VARCHAR(36);

UPDATE reactions
SET message_id = gen_random_uuid()::text
WHERE message_id IS NULL;

ALTER TABLE reactions
    ALTER COLUMN message_id SET NOT NULL,
    ADD CONSTRAINT uk_reactions_message_id UNIQUE (message_id);