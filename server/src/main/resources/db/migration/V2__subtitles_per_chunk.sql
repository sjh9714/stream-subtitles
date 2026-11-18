-- Subtitles are now saved one chunk at a time, so a row is numbered inside its chunk.
-- Rows saved before this keep chunk 0 and their video-wide numbers, which still sorts them in order.
alter table subtitle add column chunk_index int not null default 0;
alter table subtitle drop constraint subtitle_pkey;
alter table subtitle add primary key (youtube_id, chunk_index, seq);
