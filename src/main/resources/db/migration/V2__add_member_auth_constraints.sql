ALTER TABLE members
    ADD CONSTRAINT ck_members_provider_required_fields
        CHECK (
            (
                provider = 'LOCAL'
                AND login_id IS NOT NULL
                AND BTRIM(login_id) <> ''
                AND password IS NOT NULL
                AND BTRIM(password) <> ''
                AND email IS NOT NULL
                AND BTRIM(email) <> ''
                AND provider_id IS NULL
            )
            OR
            (
                provider = 'GOOGLE'
                AND login_id IS NULL
                AND password IS NULL
                AND email IS NOT NULL
                AND BTRIM(email) <> ''
                AND provider_id IS NOT NULL
                AND BTRIM(provider_id) <> ''
            )
        ),
    ADD CONSTRAINT uk_members_provider_provider_id
        UNIQUE (provider, provider_id);
