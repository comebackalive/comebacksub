alter table payment_settings rename column default_currency to currency;
--;;
alter table payment_settings rename column default_payment_amount to amount;
--;;
alter table payment_settings rename column default_card_id to card_id;
