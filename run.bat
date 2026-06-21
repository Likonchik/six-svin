@echo off
setlocal
title OneTap - runClient

rem Папка кэша Gradle (NeoForge / декомпиляция MC лежат тут)
set "GRADLE_USER_HOME=D:\gradle_home"

rem Перейти в каталог этого батника (корень проекта)
cd /d "%~dp0"

echo [OneTap] Запуск dev-клиента NeoForge 1.21.1...
call gradlew.bat runClient --no-daemon

echo.
echo [OneTap] Клиент завершён (код выхода %ERRORLEVEL%).
pause
