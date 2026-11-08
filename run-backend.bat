@echo off
title HookRelay API Backend
echo Starting HookRelay Spring Boot Backend on http://localhost:8080 (Profile: dev)...
.\mvnw.cmd spring-boot:run -pl api "-Dspring-boot.run.profiles=dev"
