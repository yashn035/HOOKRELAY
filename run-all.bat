@echo off
title HookRelay Development Environment
cd /d "%~dp0"

echo ========================================================
echo Starting HookRelay Development Environment
echo ========================================================
echo.
echo [1/2] Starting Spring Boot API Backend (Port: 8080)...
start "HookRelay API (Port 8080)" cmd /k "cd /d "%~dp0" && .\mvnw.cmd spring-boot:run -pl api -Dspring-boot.run.profiles=dev"
echo.
echo [2/2] Starting Next.js Web Dashboard (Port: 3000)...
start "HookRelay Frontend (Port 3000)" cmd /k "cd /d "%~dp0web" && npm run dev"
echo.
echo ========================================================
echo Launching in 2 separate terminal windows:
echo - Frontend:    http://localhost:3000 (or http://localhost:3001 if 3000 is in use)
echo - Backend API: http://localhost:8080
echo - Credentials: admin@hookrelay.dev / password123!
echo ========================================================
