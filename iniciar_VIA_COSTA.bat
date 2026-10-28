@echo off
setlocal
cd /d "%~dp0"
if not exist "config.properties" (
  echo Falta el archivo config.properties.
  echo Copia config.properties.example como config.properties y configura DB_PASSWORD.
  pause
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo No se encontro Maven en PATH. Abre este proyecto en IntelliJ IDEA.
  echo Ejecuta Maven ^> Plugins ^> javafx ^> javafx:run.
  pause
  exit /b 1
)
call mvn javafx:run
if errorlevel 1 pause
endlocal
