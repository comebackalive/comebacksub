create table if not exists users (
        id serial primary key,
        name text,
        email text not null,
        phone text,
        created_at timestamp with time zone not null default now(),
        updated_at timestamp with time zone
);

create type transaction_type as enum ('Scheduled','Created','Settled','InProcessing','Approved','Pending', 'Expired', 'Refunded', 'Voided', 'Declined');

create table if not exists transaction_log (
   id               serial primary key,
   transaction      uuid  not null,
   amount           integer not null,
   type             transaction_type,
   user_id          integer references users (id) not null,
   created_at       timestamp with time zone not null default now()
);


create table if not exists cards (
   id               serial primary key,
   user_id          integer references users (id) not null,
   token            text not null,
   card_pan         text,
   card_info        jsonb,
   is_deleted       boolean default false,
   created_at       timestamp with time zone not null default now()
);

create table if not exists payment_settings (
      id               serial primary key,
      default_card_id  integer references cards(id),
      user_id          integer references users(id),  
      shedule_offset   integer,
      created_at       timestamp with time zone not null default now(),
      updated_at       timestamp with time zone
);

