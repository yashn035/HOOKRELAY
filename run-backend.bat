@echo off
title HookRelay API Backend
cd /d "%~dp0"
echo Starting HookRelay Spring Boot Backend on http://localhost:8080 (Profile: dev)...
.\mvnw.cmd spring-boot:run -pl api -Dspring-boot.run.profiles=dev
