#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
source_dir=app/src/main/java/ru/logunov/bydsplit
"${java_bin}javac" -d "$test_classes" \
  "$source_dir/VehicleTelemetrySnapshot.java" "$source_dir/ClimateIconState.java" \
  "$source_dir/BatteryDetailsSnapshot.java" "$source_dir/TechSnapshot.java" \
  "$source_dir/DmiEnergyFlow.java" "$source_dir/TemperatureRules.java" \
  "$source_dir/MaxCallClassifier.java" "$source_dir/MaxTaskParser.java" tests/*.java
for test_file in tests/*Test.java; do
  test_class=$(basename "$test_file" .java)
  "${java_bin}java" -cp "$test_classes" "ru.logunov.bydsplit.$test_class"
done
