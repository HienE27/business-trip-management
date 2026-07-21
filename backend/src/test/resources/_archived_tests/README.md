# Archived Tests

This directory contains JUnit test classes that have been moved out of the
Surefire scan path because they cannot load in their original location.

## Why these tests are archived

The original location was `src/test/java/com/hospital/scheduler/service/scheduling/disabled-tests/`.
That directory name contains a hyphen, which Maven Surefire 3.x interprets as
a non-package path. The classes inside declared
`package com.hospital.scheduler.service.scheduling;`, so when Surefire tried
to resolve the test class against its package, it threw
`NoClassDefFoundError` before the test runner could read the `@Disabled`
annotation.

The tests were already non-functional in CI; they failed every run with a
load error rather than running.

## Files

- `ReplacementSuggestionServiceTest.java` — targets the replacement-suggestion
  service reachable via `LeaveRequestService.findReplacementsForLeave` and the
  M07-F08 wizard.
- `UnassignedDaysReportBuilderTest.java` — targets the unassigned-days report
  builder used in dashboard reporting.

## How to re-enable

1. Move the file back under `src/test/java/` at a directory that matches the
   declared package (e.g. `src/test/java/com/hospital/scheduler/service/scheduling/`).
2. Remove the `@Disabled` annotation from the class.
3. If the underlying service contract has changed, update the test.
4. Run `mvn test -Dtest=<ClassName>` to confirm green.

## Why not delete the files

These tests document the expected contract for code paths that are reachable
through other entry points in the application. Keeping them around (even when
disabled) preserves the regression net for when those paths are wired back in.