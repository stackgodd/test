#!/usr/bin/env bash
# Local build for macOS/Linux. Needs Java 25.
set -e
echo "Which Minecraft version?  1) 26.1.2   2) 26.2   3) 26.3"
read -p "Pick 1, 2 or 3: " c
case "$c" in
  1) MC=26.1.2; API="0.155.3+26.1.2"; RANGE="~26.1";;
  2) MC=26.2;   API="0.161.0+26.2";   RANGE="~26.2";;
  3) MC=26.3;   API="0.165.0+26.3";   RANGE="~26.3";;
  *) echo "Invalid choice"; exit 1;;
esac
chmod +x ./gradlew
./gradlew build -Pminecraft_version=$MC -Pfabric_api_version="$API" -Pmc_range="$RANGE" -Pversion=1.0.0+mc$MC
mkdir -p mods-output
cp "build/libs/auction-house-timer-1.0.0+mc$MC.jar" mods-output/
echo "Done! Copy the jar from ./mods-output into your Minecraft mods folder."
