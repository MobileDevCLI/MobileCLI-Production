# MobileCLI Golden Reference - v1.6.1-fix

> **DO NOT MODIFY** - This is the verified working version

## APK Details

| Property | Value |
|----------|-------|
| **File** | `MobileCLI-v1.6.1-GOLDEN.apk` |
| **MD5** | `d473f8f5c3f06a42fb8fd4d5aa79bd91` |
| **Size** | 6,877,419 bytes |
| **Internal Version** | `mobilecli-v66` |
| **Status** | 21/21 test categories PASSED |

## What This Version Has

### Bootstrap Flow
1. Setup overlay (hides terminal during install)
2. Downloads 50MB bootstrap
3. Progress: 0% → 100% with status messages
4. AI Selection: Claude | Gemini | Codex | None
5. Welcome animation (5 seconds)
6. Clean terminal launch

### IP Protection
- Setup overlay hides ALL technical output
- motd: "Welcome to MobileCLI - AI-Powered Terminal"
- PS1: `user@mobilecli:~$` (no paths revealed)
- Developer mode: 7-tap unlock
- New tabs show welcome message

### Features
- 42 API scripts (termux-clipboard-*, termux-toast, etc.)
- Claude Power Mode (--dangerously-skip-permissions)
- Wake lock support
- Up to 10 terminal sessions
- Text size: 14-56

## Decompiled Source

Available at: `~/v161-decompiled/sources/com/termux/`

Key files:
- `MainActivity.java` (227 KB)
- `BootstrapInstaller.java` (109 KB)
- `SetupWizardActivity.java` (45 KB)

## Why This Version

This is the **last known stable version** before corruption issues.
All future development should branch from this baseline.

---

**Preserved:** January 16, 2026
**Session:** Resumed from January 10, 2026
