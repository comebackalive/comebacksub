create type currency_type as enum ('UAH', 'RUB', 'USD', 'EUR', 'GBP', 'CZK');

--;;

alter table transaction_log add column currency currency_type;
