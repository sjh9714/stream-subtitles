-- One row per call to a model, whatever became of its reply, so a month's spending counts every call.
create table model_call (
    id            bigint generated always as identity primary key,
    model         text           not null,
    input_tokens  bigint         not null,
    output_tokens bigint         not null,
    usd           numeric(12, 6) not null,
    created_at    timestamptz    not null default now()
);

create index model_call_created_at on model_call (created_at);
