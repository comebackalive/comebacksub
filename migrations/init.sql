create table if not exists subscription (
   id                  serial primary key,
   token               text,
   amount              integer not null,
   period              integer not null,
   data                jsonb,
   deleted_at          timestamp with time zone,
   created_at          timestamp with time zone not null default now()
);

create unique index on subscription(token,amount,period) where deleted_at is null;

create table if not exists payment (
   uuid                uuid primary key
   reference           text
   subscription_id     integer references subscription(id)
   data                jsonb
   amount              integer not null,
   deleted_at          timestamp with time zone,
   created_at          timestamp with time zone not null default now()
);
