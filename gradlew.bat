@echo off
setlocal
set "GRADLE_VERSION=9.6.1"
set "DIST_ROOT=C:\JavaProgram\FreeCoreShop\.gradle-dist"
set "GRADLE_USER_HOME=C:\JavaProgram\FreeCoreShop\.gradle-user-home"
set "GRADLE_HOME=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Error: Gradle not found at %GRADLE_HOME%
  exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" --gradle-user-home "%GRADLE_USER_HOME%" %*
