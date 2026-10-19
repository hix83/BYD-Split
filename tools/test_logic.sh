#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
source_dir=app/src/main/java/ru/logunov/bydsplit
"${java_bin}javac" -encoding UTF-8 -d "$test_classes" \
  "$source_dir/AdbHelperRecovery.java" "$source_dir/VehicleTelemetrySnapshot.java" "$source_dir/ClimateIconState.java" \
  "$source_dir/BatteryDetailsSnapshot.java" "$source_dir/TechSnapshot.java" \
  "$source_dir/ClusterTrackText.java" "$source_dir/UpdateVersion.java" "$source_dir/SeagullUpdateChannel.java" "$source_dir/AdbRemoteWatchdogPolicy.java" "$source_dir/EmbeddedImmersivePolicy.java" \
  "$source_dir/DmiEnergyFlow.java" "$source_dir/TemperatureRules.java" \
  "$source_dir/MaxCallClassifier.java" "$source_dir/MaxTaskParser.java" \
  "$source_dir/ThemePolicy.java" "$source_dir/Di2VehicleSdk.java" \
  "$source_dir/Di2BatteryValues.java" "$source_dir/DiPlusValues.java" "$source_dir/Di2Snapshot.java" "$source_dir/EmbeddedTaskParser.java" tests/*.java
for test_file in tests/*Test.java; do
  test_class=$(basename "$test_file" .java)
  "${java_bin}java" -cp "$test_classes" "ru.logunov.bydsplit.$test_class"
done
