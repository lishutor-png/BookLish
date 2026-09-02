#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
#
# Gradle start up script for POSIX
#

# Set default values for variables
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

# Use standard gradle if wrapper jar is not present, or invoke gradle wrapper
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    JAVACMD="java"
    if [ -n "$JAVA_HOME" ] ; then
        if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
            JAVACMD="$JAVA_HOME/jre/sh/java"
        else
            JAVACMD="$JAVA_HOME/bin/java"
        fi
    fi
    exec "$JAVACMD" -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
else
    exec gradle "$@"
fi
