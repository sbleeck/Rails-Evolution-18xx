#!/bin/bash

# --- CONFIGURATION ---
GAME_JAR="./build/libs/rails-all.jar"
SOURCE_FILE="./src/RegressionTest.java"
CLASSPATH="src:$GAME_JAR:./lib/*"
TEST_DIR="./testgames"

# 1. Compile (Only once!)
echo "Compiling RegressionTest..."
javac -cp "$CLASSPATH" "$SOURCE_FILE"
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# 2. Loop through files in the shell
echo "Starting Regression Suite (Process Isolation Mode)..."
count=0
passed=0
failed=0

# Find all .rails files and loop
for file in "$TEST_DIR"/*.rails; do
    ((count++))
    filename=$(basename "$file")
    
    echo "------------------------------------------------------------"
    echo "Processing [$count]: $filename"
    
 # --- START FIX ---
    # Capture output to check for "leaked" errors that bypass the Java capture
    output=$(java -cp "$CLASSPATH" -Djava.awt.headless=true RegressionTest "$file" 2>&1)
    exit_code=$?

    # Print the output so you can still see it
    echo "$output"

    # Fail if Java exited with error OR if keywords leaked to the console
if [ $exit_code -ne 0 ] || echo "$output" | grep -qE "FATAL|CRASH|VALIDATION FAILURE|Exception|RELOAD ERROR|Action not in PossibleActions"; then
        echo "RESULT: FAILED"
        ((failed++))
    else
        echo "RESULT: OK"
        ((passed++))
    fi
    # --- END FIX ---
    
    # Check exit code of the Java process
    # (The previous check block is replaced by the logic above)
done

echo ""
echo "=== SUITE SUMMARY ==="
echo "Total:  $count"
echo "Passed: $passed"
echo "Failed: $failed"

if [ $failed -gt 0 ]; then
    exit 1
fi