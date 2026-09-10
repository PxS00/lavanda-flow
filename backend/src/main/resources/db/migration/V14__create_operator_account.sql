CREATE TABLE operator_account (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT operator_account_username_not_blank
        CHECK (btrim(username) <> ''),
    CONSTRAINT operator_account_password_hash_not_blank
        CHECK (btrim(password_hash) <> '')
);

CREATE UNIQUE INDEX operator_account_username_lower_uk
    ON operator_account (lower(username));
