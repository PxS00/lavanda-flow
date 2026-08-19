-- Spring Modulith JPA Event Publication Registry infrastructure.
--
-- This table is framework-owned infrastructure required by the approved
-- spring-modulith-starter-jpa dependency. Flyway remains the schema source
-- of truth; Hibernate only validates the resulting schema.

CREATE TABLE event_publication
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);

CREATE INDEX event_publication_by_completion_date_idx
    ON event_publication (completion_date);
