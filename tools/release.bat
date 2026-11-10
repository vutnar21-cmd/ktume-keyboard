@echo off
REM One-command local release launcher for Kesje Tume (org.chuvash.ktume).
REM Bypasses Windows PowerShell ExecutionPolicy restrictions safely for tools\release.ps1.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" %*
exit /b %ERRORLEVEL%
