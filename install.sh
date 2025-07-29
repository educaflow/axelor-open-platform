#!/bin/bash
clear
echo "Matando procesos gradle y node colgados..."
pkill -f '.*gradle.*' 2>/dev/null
pkill -f '.*node.*' 2>/dev/null

export NODE_OPTIONS=--max-old-space-size=9216
export GRADLE_OPTS="-Xmx9g"

./gradlew --no-daemon clean
./gradlew --no-daemon build --stacktrace
./gradlew --no-daemon publishToMavenLocal

#./gradlew --no-daemon build --stacktrace -x test
#./gradlew --no-daemon publishToMavenLocal