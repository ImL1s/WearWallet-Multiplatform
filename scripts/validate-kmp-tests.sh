#!/bin/bash

# KMP Integration Tests Validation Script
# This script runs all integration tests and generates a comprehensive report

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_DIR="$PROJECT_ROOT/build/test-reports"
SUMMARY_FILE="$REPORT_DIR/kmp-test-summary-$TIMESTAMP.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   KMP Integration Tests Validator     ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Create report directory
mkdir -p "$REPORT_DIR"

# Function to run tests and capture results
run_test_suite() {
    local module=$1
    local test_class=$2
    local description=$3
    
    echo -e "${YELLOW}Running: $description${NC}"
    echo "Module: $module"
    echo "Test Class: $test_class"
    echo ""
    
    if ./gradlew :$module:testDebugUnitTest --tests "$test_class" > "$REPORT_DIR/test-$test_class.log" 2>&1; then
        echo -e "${GREEN}✅ PASSED: $description${NC}"
        echo "✅ PASSED: $description" >> "$SUMMARY_FILE"
        return 0
    else
        echo -e "${RED}❌ FAILED: $description${NC}"
        echo "❌ FAILED: $description" >> "$SUMMARY_FILE"
        # Extract failure details
        tail -20 "$REPORT_DIR/test-$test_class.log" | grep -E "FAILED|error|Error" >> "$SUMMARY_FILE" 2>/dev/null || true
        return 1
    fi
}

# Start summary report
echo "KMP Integration Test Summary - $TIMESTAMP" > "$SUMMARY_FILE"
echo "===========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Track overall results
TOTAL_SUITES=0
PASSED_SUITES=0
FAILED_SUITES=0

# Core Integration Tests
echo -e "${BLUE}=== Core Integration Tests ===${NC}"
echo ""

# Test SimplifiedDataFlowTest
((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.integration.SimplifiedDataFlowTest" "Simplified Data Flow Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Test SimplifiedRepositoryTest
((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.integration.SimplifiedRepositoryTest" "Simplified Repository Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Test SimplifiedErrorHandlingTest
((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.integration.SimplifiedErrorHandlingTest" "Simplified Error Handling Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Test SimplifiedKoinIntegrationTest
((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.di.SimplifiedKoinIntegrationTest" "Simplified Koin Integration Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Test SimpleViewModelBridgeTest
((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.presentation.wallet.screens.main.SimpleViewModelBridgeTest" "Simple ViewModel Bridge Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Performance Tests
echo -e "${BLUE}=== Performance Tests ===${NC}"
echo ""

((TOTAL_SUITES++))
if run_test_suite "wear" "com.cbstudio.wearwallet.integration.PerformanceBenchmarkTest" "Performance Benchmark Test"; then
    ((PASSED_SUITES++))
else
    ((FAILED_SUITES++))
fi
echo ""

# Run all tests together for coverage
echo -e "${BLUE}=== Running Full Test Suite ===${NC}"
echo ""

if ./gradlew :wear:testDebugUnitTest > "$REPORT_DIR/full-test-run.log" 2>&1; then
    echo -e "${GREEN}✅ Full test suite execution completed${NC}"
else
    echo -e "${YELLOW}⚠️  Some tests failed in full suite${NC}"
fi

# Extract test statistics from HTML report
if [ -f "$PROJECT_ROOT/wear/build/reports/tests/testDebugUnitTest/index.html" ]; then
    echo "" >> "$SUMMARY_FILE"
    echo "Full Test Suite Statistics:" >> "$SUMMARY_FILE"
    echo "===========================" >> "$SUMMARY_FILE"
    
    # Parse HTML for statistics (basic extraction)
    grep -oP 'class="counter">\K[^<]+' "$PROJECT_ROOT/wear/build/reports/tests/testDebugUnitTest/index.html" | head -4 | {
        read total_tests
        read failures
        read ignored
        read duration
        
        echo "Total Tests: $total_tests" >> "$SUMMARY_FILE"
        echo "Failures: $failures" >> "$SUMMARY_FILE"
        echo "Ignored: $ignored" >> "$SUMMARY_FILE"
        echo "Duration: $duration" >> "$SUMMARY_FILE"
        
        if [ "$failures" = "0" ]; then
            SUCCESS_RATE="100%"
        else
            # Calculate success rate
            PASSED=$((total_tests - failures))
            SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f%%\", ($PASSED/$total_tests)*100}")
        fi
        echo "Success Rate: $SUCCESS_RATE" >> "$SUMMARY_FILE"
    }
fi

# Generate final summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}           Test Summary                ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Test Suites Run: $TOTAL_SUITES"
echo -e "${GREEN}Passed: $PASSED_SUITES${NC}"
echo -e "${RED}Failed: $FAILED_SUITES${NC}"
echo ""

# Append to summary file
echo "" >> "$SUMMARY_FILE"
echo "Test Suite Summary:" >> "$SUMMARY_FILE"
echo "==================" >> "$SUMMARY_FILE"
echo "Total Suites: $TOTAL_SUITES" >> "$SUMMARY_FILE"
echo "Passed Suites: $PASSED_SUITES" >> "$SUMMARY_FILE"
echo "Failed Suites: $FAILED_SUITES" >> "$SUMMARY_FILE"

# Check if we should generate coverage report
echo ""
echo -e "${BLUE}Checking for coverage report generation...${NC}"
if ./gradlew :wear:koverHtmlReport > "$REPORT_DIR/coverage-generation.log" 2>&1; then
    echo -e "${GREEN}✅ Coverage report generated${NC}"
    echo "Coverage report: $PROJECT_ROOT/wear/build/reports/kover/html/index.html"
    echo "" >> "$SUMMARY_FILE"
    echo "Coverage report generated at: wear/build/reports/kover/html/index.html" >> "$SUMMARY_FILE"
else
    echo -e "${YELLOW}⚠️  Coverage report generation failed${NC}"
fi

# Final status
echo ""
echo -e "${BLUE}========================================${NC}"
if [ $FAILED_SUITES -eq 0 ]; then
    echo -e "${GREEN}✅ All test suites passed!${NC}"
    echo "" >> "$SUMMARY_FILE"
    echo "✅ VALIDATION SUCCESSFUL - All test suites passed!" >> "$SUMMARY_FILE"
    EXIT_CODE=0
else
    echo -e "${RED}❌ Some test suites failed${NC}"
    echo "" >> "$SUMMARY_FILE"
    echo "❌ VALIDATION FAILED - $FAILED_SUITES test suite(s) failed" >> "$SUMMARY_FILE"
    EXIT_CODE=1
fi
echo -e "${BLUE}========================================${NC}"

# Display summary file location
echo ""
echo "Full report saved to: $SUMMARY_FILE"
echo "Test logs saved in: $REPORT_DIR"
echo "HTML test report: $PROJECT_ROOT/wear/build/reports/tests/testDebugUnitTest/index.html"

# Open HTML report if on macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo ""
    echo -e "${BLUE}Opening HTML test report...${NC}"
    open "$PROJECT_ROOT/wear/build/reports/tests/testDebugUnitTest/index.html" 2>/dev/null || true
fi

exit $EXIT_CODE