create table tokens (
  id serial primary key,
  data text not null,
  created_at timestamptz not null default now());
