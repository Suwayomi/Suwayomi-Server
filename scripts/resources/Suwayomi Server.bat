@echo off
set auto_update=1

:: Move CWD to script folder
pushd "%~0\.."

:: Auto-Updater
if %auto_update% NEQ 1 goto :launch
for /f "tokens=2" %%i in ('curl -s https://api.github.com/repos/Suwayomi/Suwayomi-Server-preview/releases/latest^|findstr "https://.*jar"') do (
	pushd bin
	if NOT EXIST "%%~nxi" (
    echo Updating to %%~ni
		del /q "*.jar"
		curl -sL %%i -o "%%~nxi"
		mklink /h "Suwayomi-Server.jar" "%%~nxi"
	)
	popd
)

:launch
start "" jre\bin\javaw %* -jar Suwayomi-Launcher.jar --launch
popd
