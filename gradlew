#!/usr/bin/env sh
set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)

if [ -z "${GRADLE_USER_HOME:-}" ]; then
  GRADLE_USER_HOME="$APP_HOME/.bear-gradle-user-home"
  export GRADLE_USER_HOME
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
else
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
