#!/bin/sh
# Gradle wrapper script
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME=`pwd -P`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Java options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )  cygwin=true  ;;
  Darwin* )  darwin=true  ;;
  MINGW* )   msys=true    ;;
  NONSTOP* ) nonstop=true ;;
esac

JAVA_HOME_VAR="${JAVA_HOME}"
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="`which java`"
fi

if [ ! -x "$JAVACMD" ] ; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Gradle wrapper jar download
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -o "$WRAPPER_JAR"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -O "$WRAPPER_JAR"
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
