UPDATE staff SET password_hash='$2a$10$hl1DhCs/R5yXeXtV8ABlaOgvS/.verHfCiOpkpGrjcUIqiX.a9/Qe' WHERE username='admin';
SELECT username, password_hash FROM staff WHERE username='admin';