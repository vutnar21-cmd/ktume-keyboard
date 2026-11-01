@echo off
REM One-command local release launcher for Kesje Tume (org.chuvash.ktume).
REM Bypasses Windows PowerShell ExecutionPolicy restrictions safely for scripts\release.ps1.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" %*
exit /b %ERRORLEVEL%
