# Project Instructions

## E2E compatibility gate

- This repository is being used for an E2E load-testing competition. The E2E tests from the original codebase are the immutable minimum acceptance baseline.
- Never modify, delete, skip, quarantine, weaken, or relax existing E2E tests, their assertions, fixtures, or runner configuration in order to make an implementation pass.
- Make performance and behavior changes only in the application or load-test implementation while preserving the behavior required by the unchanged E2E suite.
- Preserve observable request flows that existing E2E tests explicitly depend on, including `/api/health` behavior, unless the user explicitly changes this competition constraint.
- Before completing an implementation change, run the relevant existing E2E tests unchanged when feasible. If they cannot be run, clearly report that verification gap.
- If a requested change conflicts with an existing E2E contract, stop and explain the conflict instead of editing or bypassing the test.
