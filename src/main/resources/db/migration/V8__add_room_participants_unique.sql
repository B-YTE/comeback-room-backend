ALTER TABLE room_participants
    ADD CONSTRAINT uk_room_participants_room_member UNIQUE (room_id, member_id);