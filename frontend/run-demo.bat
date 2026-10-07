@echo off
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel% equ 0 (
  python -m http.server 5500
) else (
  node dev-server.js
)
