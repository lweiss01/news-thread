# Agent Instructions

## MANDATORY RULES - READ FIRST

**The user is in charge. The agent works FOR the user.**

1. **ASK before changing anything**
   - Explain what you found
   - Propose ONE specific fix
   - WAIT for approval before writing code
   - Never assume you know what the user wants

2. **Read git history FIRST**
   - Run `git log --oneline -10` before any fix
   - Check what was fixed in recent commits
   - NEVER break something that was already fixed
   - If you see a similar fix, ask if it's related

3. **Test EVERY change before claiming it works**
   - Build the code
   - Install the app
   - Verify the fix in the running app
   - NEVER say "this should work" - verify it actually does

4. **ONE change per commit**
   - Never bundle multiple fixes
   - Never fix "while you're at it" 
   - Each commit should do ONE thing

5. **Follow the process even when you think you know better**
   - These rules exist because you've broken things before
   - No shortcuts
   - No assumptions
   - Ask questions

---

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>         # Complete work
bd sync               # Sync with git
```

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

