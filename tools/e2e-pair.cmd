@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0e2e-pair.ps1" %*
