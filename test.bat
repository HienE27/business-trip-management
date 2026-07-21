@echo off
curl.exe -s -X POST "http://localhost:8080/api/v1/auth/login" -H "Content-Type: application/json" -d "@%~dp0login.json" > "%~dp0login.body"
exit /b