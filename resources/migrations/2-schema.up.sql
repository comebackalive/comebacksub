alter table transaction_log add column data jsonb;
--;;

create unique index on payment_settings(user_id);

--;;

create unique index on cards(user_id,card_pan);

--;;
alter table transaction_log add column scheduled_for timestamp with time zone;

--;;
alter table transaction_log add column card_id integer references cards(id);

--;;
alter table transaction_log add column order_id text not null;

--;;
alter table cards add column type integer references cards(id);

--;;
create type card_type as enum ('kastapay','fondy');
--;;

create index transaction_log_scheduled_for_date_utc
  on transaction_log(date(timezone('UTC',scheduled_for))) where scheduled_for is not null;



