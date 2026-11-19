ALTER TABLE poll_options
    ADD CONSTRAINT uk_poll_options_poll_id_content UNIQUE (poll_id, content);