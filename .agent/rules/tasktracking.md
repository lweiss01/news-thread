---
trigger: always_on
---

## Project Rules
- **Task Tracking**:
    - **GSD** (`/gsd:*`): For planned phase work.
    - **Beads** (`bd`): For ad-hoc bugs and ideas.
    - **NO TODOs**: Do not use TODO comments for work tracking. Do not use GSD Todo (/gsd:add-todo) for work tracking.

- **Git Commits**:
    - After executing a phase (`/gsd:execute-phase`), stage and prepare all modified files in app/ along with the planning docs to be pushed to github in a single commit. When the files are staged, ask for permission to commit and push all changes to github.
    - Do not commit any changes to github without permssion. Always wait for approval to commit.