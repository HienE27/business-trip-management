SELECT username, SUBSTRING(password_hash,1,30) AS hash_start FROM hospital_scheduler_stg.staff WHERE username IN ('admin','manager1','manager2');
