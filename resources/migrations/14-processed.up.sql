-- to identify that status was processed, not only stored
alter table transaction_log add column processed boolean; 
