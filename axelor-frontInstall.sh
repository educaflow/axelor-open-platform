#!/bin/bash

export NODE_OPTIONS=--max-old-space-size=4096

./gradlew :axelor-front:buildFront
./gradlew :axelor-web:build --stacktrace
./gradlew :axelor-web:publishToMavenLocal