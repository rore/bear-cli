@echo off
setlocal

set "APP_HOME=%~dp0"

rem Prefer a repo-local Gradle user home to avoid permission issues and stray global caches.
rem Users can override by setting GRADLE_USER_HOME explicitly.
if not defined GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%TEMP%\bear-cli-gradle-home"
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

endlocal
