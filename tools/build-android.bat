@echo off
setlocal

set "PROJECT_ROOT=%~dp0.."
set "ISOLATED_BUILD_ROOT=%LOCALAPPDATA%\NourishRx\cli-build"

call "%PROJECT_ROOT%\gradlew.bat" --no-daemon "-Pnourishrx.isolatedBuildRoot=%ISOLATED_BUILD_ROOT%" %*
exit /b %ERRORLEVEL%
