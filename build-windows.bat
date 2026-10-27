@echo off
setlocal
echo.
echo  Auction House Timer - local build
echo  Needs Java 25 installed (https://adoptium.net  ->  Temurin 25).
echo.
echo  Which Minecraft version?
echo    1) 26.1.2   (also fine for 26.1 / 26.1.1)
echo    2) 26.2
echo    3) 26.3
set /p CHOICE=Pick 1, 2 or 3: 
if "%CHOICE%"=="1" set MC=26.1.2& set API=0.155.3+26.1.2& set RANGE=~26.1
if "%CHOICE%"=="2" set MC=26.2& set API=0.161.0+26.2& set RANGE=~26.2
if "%CHOICE%"=="3" set MC=26.3& set API=0.165.0+26.3& set RANGE=~26.3
if not defined MC (echo Invalid choice.& pause& exit /b 1)
call gradlew.bat build -Pminecraft_version=%MC% -Pfabric_api_version=%API% -Pmc_range=%RANGE% -Pversion=1.0.0+mc%MC%
if errorlevel 1 (echo.& echo BUILD FAILED - copy the red error text and send it to me.& pause& exit /b 1)
if not exist mods-output mkdir mods-output
copy /Y build\libs\auction-house-timer-1.0.0+mc%MC%.jar mods-output\ >nul
echo.
echo  Done! Your mod is in the "mods-output" folder. Copy that .jar into your Minecraft "mods" folder.
start "" mods-output
pause
