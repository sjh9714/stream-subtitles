-- Terms the server extracted from a channel's videos. The hand-written terms in glossary.json win over them.
create table channel_term (
    channel    text        not null,
    heard      text        not null,
    written    text        not null,
    about      text        not null,
    youtube_id varchar(16) not null,
    created_at timestamptz not null default now()
);

-- The first written form of a heard form stays, whatever its letter case.
create unique index channel_term_heard on channel_term (channel, lower(heard));

-- One row per video whose terms were extracted, kept to extract once per video and to track the cost.
create table term_extraction (
    youtube_id    varchar(16) primary key references video on delete cascade,
    input_tokens  bigint      not null,
    output_tokens bigint      not null,
    millis        bigint      not null
);
