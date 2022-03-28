alter table cards add column card_label text;
--;;
update cards set card_label = card_pan;
