@echo off
title HookRelay Development Environment
echo ========================================================
echo Starting HookRelay Development Environment
echo ========================================================
echo.
echo [1/2] Starting Spring Boot API Backend (Port: 8080, Profile: dev)...
start "HookRelay API (Port 8080)" cmd /k ".\mvnw.cmd spring-boot:run -pl api "-Dspring-boot.run.profiles=dev""
echo.
echo [2/2] Starting Next.js Web Dashboard (Port: 3000)...
start "HookRelay Frontend (Port 3000)" cmd /k "cd web && npm run dev"
echo.
echo ========================================================
echo Servers are launching in separate terminal windows:
echo - Web Dashboard: http://localhost:3000
echo - API Health:    http://localhost:8080/health
echo - Swagger UI:    http://localhost:8080/swagger-ui.html
echo - Credentials:   admin@hookrelay.dev / password123!
echo ========================================================
