-- When a model call was made, so that the cost of one month can be summed.
alter table translation_chunk add column created_at timestamptz not null default now();
alter table term_extraction add column created_at timestamptz not null default now();
