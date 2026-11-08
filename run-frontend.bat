@echo off
title HookRelay Next.js Frontend
cd /d "%~dp0web"
echo Starting HookRelay Next.js Frontend on http://localhost:3000 (or http://localhost:3001 if 3000 is in use)...
npm run dev
