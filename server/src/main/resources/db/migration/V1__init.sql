-- Only the video id, its channel and the translated result are stored. The English captions are not.
create table video (
    youtube_id  varchar(16) primary key,
    channel     text        not null,
    status      varchar(16) not null,
    error       text,
    created_at  timestamptz not null default now(),
    finished_at timestamptz
);

create table subtitle (
    youtube_id varchar(16) not null references video on delete cascade,
    seq        int         not null,
    start_ms   bigint      not null,
    end_ms     bigint      not null,
    text       text        not null,
    primary key (youtube_id, seq)
);

-- One row per model call, kept to track what a video hour costs.
create table translation_chunk (
    youtube_id    varchar(16) not null references video on delete cascade,
    chunk_index   int         not null,
    input_tokens  bigint      not null,
    output_tokens bigint      not null,
    millis        bigint      not null,
    primary key (youtube_id, chunk_index)
);
