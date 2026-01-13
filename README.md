# MobileCLI Production

Production-ready rebuild of MobileCLI with clean source code.

## Version
- **Version Code:** 162
- **Version Name:** 2.0.0-production
- **Status:** Phase 1 Complete

## Features

### Core Terminal
- TerminalView from Termux libraries (Apache 2.0)
- Multi-session support (up to 10 sessions)
- Session tabs with color indicators
- Pinch-to-zoom text sizing (14-56pt)
- Extra keys row (ESC, CTRL, ALT, TAB, arrows)

### Navigation Drawer
- Swipe from left edge
- New Session
- Settings
- Keyboard toggle
- Text Size
- Install AI (admin only)
- Help / About
- Wake Lock toggle
- Claude Power Mode toggle
- Developer Mode (hidden, 7-tap activation)
- Install Dev Tools (dev mode only)
- Sessions list

### AI Integration
- AI choice cards (Claude, Gemini, Codex, Terminal)
- Welcome overlay for IP protection
- Automatic AI installation commands

### Admin/Developer Mode
- 7-tap version to enable
- MD5 password protection (default: 1234)
- Hidden "Install AI" and "Install Dev Tools" options

### Power Features
- Wake Lock for CPU
- Claude Power Mode (--dangerously-skip-permissions)

## Technical Requirements

**CRITICAL:**
- Package name MUST be `com.termux` (RUNPATH requirement)
- targetSdkVersion MUST be 28 (exec() requirement)

## Project Structure

```
MobileCLI-Production/
├── app/
│   ├── build.gradle.kts          # App configuration
│   ├── proguard-rules.pro        # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml   # 45+ permissions
│       ├── assets/
│       │   ├── scripts/          # API scripts
│       │   └── termux-am/        # Activity manager
│       ├── java/com/termux/
│       │   ├── MainActivity.kt           # Terminal UI (800+ lines)
│       │   ├── TermuxApplication.kt      # App lifecycle
│       │   ├── BootstrapInstaller.kt     # Bootstrap download/install
│       │   ├── app/
│       │   │   └── TermuxService.kt      # Background service
│       │   ├── auth/                     # (Phase 2)
│       │   └── payment/                  # (Phase 3)
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml     # Main UI
│           │   ├── overlay_setup.xml     # Setup progress
│           │   └── overlay_ai_choice.xml # AI selection
│           ├── drawable/                 # Icons, backgrounds
│           ├── values/                   # Strings, colors, themes
│           └── values-night/             # Dark mode colors
├── gradle/                        # Gradle wrapper
├── docs/                          # Documentation
├── legal/                         # Terms, Privacy, EULA
├── build.gradle.kts               # Root build file
├── settings.gradle.kts            # Module settings
└── gradle.properties              # Build properties
```

## Dependencies

```kotlin
// Terminal (Apache 2.0)
implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")

// AndroidX
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.11.0")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Payments (Phase 3)
implementation("com.stripe:stripe-android:20.36.0")
```

## Build Flavors

- **userDebug/userRelease** - Production build, dev mode OFF
- **devDebug/devRelease** - Development build, dev mode ON

## Build Commands

```bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk

# User build
./gradlew :app:assembleUserDebug

# Copy to Downloads
cp app/build/outputs/apk/user/debug/app-user-debug.apk /sdcard/Download/MobileCLI-v162.apk
```

## Phases

- [x] Phase 1: Core Terminal & Bootstrap
- [ ] Phase 2: Authentication (Supabase)
- [ ] Phase 3: Payments (Stripe)
- [ ] Phase 4: Polish & Testing

## Source of Truth

This codebase replaces the decompiled v161. All features are being rebuilt with clean, documented Kotlin code.

---

*Created: January 12, 2026*
