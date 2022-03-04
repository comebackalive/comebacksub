alter table payment_settings add column default_currency currency_type;
--;;
alter table payment_settings add column frequency text;
--;;
alter table payment_settings add column next_payment_at timestamp with time zone; 
--;;
create index payment_settings_next_payment_at_date_utc
  on payment_settings(date(timezone('UTC',next_payment_at))) where next_payment_at is not null;
--;;
drop index transaction_log_scheduled_for_date_utc;
