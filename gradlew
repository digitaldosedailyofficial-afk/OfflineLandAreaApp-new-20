#!/bin/sh
##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

APP_HOME=$( cd "$( dirname "$0" )" && pwd )
JAVA_CMD="java"

exec "$JAVA_CMD" -Xmx64m -Xms64m -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
