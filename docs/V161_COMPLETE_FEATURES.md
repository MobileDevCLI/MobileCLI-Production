# MobileCLI v161 - Complete Feature Audit

## Source Files Analyzed

| File | Lines | Size | Purpose |
|------|-------|------|---------|
| MainActivity.java | 4,111 | 222KB | Terminal UI, sessions, AI, drawer |
| BootstrapInstaller.java | 931 | 107KB | Bootstrap download, API scripts |
| SetupWizardActivity.java | ~400 | 45KB | Permissions, setup flow |
| LicenseManager.java | ~250 | 22KB | Supabase license verification |
| TermuxApiReceiver.java | ~2000 | 129KB | 60+ API handlers |

---

## 1. MainActivity Features (4,111 lines)

### 1.1 Terminal UI
- **TerminalView** - Full terminal emulator display
- **DrawerLayout** - Swipe-from-left navigation drawer
- **Extra Keys Row** - ESC, TAB, CTRL, ALT, arrows, special chars
- **Session Tabs** - HorizontalScrollView with colored tabs
- **Text Size** - Pinch to zoom (14-48pt range)
- **Keyboard Handling** - Show/hide, visibility detection

### 1.2 Multi-Session Support
- **Max 10 sessions** - Configurable limit
- **Session management:**
  - `addNewSession()` - Create new session
  - `switchToSession(index)` - Switch between sessions
  - `killCurrentSession()` - Terminate current
  - `removeSession(index)` - Remove from list
- **Session persistence** - Survives app close via TermuxService
- **Transcript save/restore** - Saved to SharedPreferences

### 1.3 Navigation Drawer
```
Items:
├── + New Session
├── ⚙️ Settings
├── ⌨️ Keyboard
├── 🔤 Text Size
├── 🤖 Install AI
├── ❓ Help
├── ℹ️ About
├── 🔒 Wake Lock (toggle)
├── ⚡ Claude Power Mode (toggle)
├── 🛠️ Developer Mode (hidden)
├── 📦 Install Dev Tools (dev mode only)
└── 📋 Sessions List
```

### 1.4 AI Integration
- **AI Choices:** Claude, Gemini, Codex, None
- **AI Installation Commands:**
  - Claude: `pkg update && pkg install -y nodejs && npm install -g @anthropic-ai/claude-code`
  - Gemini: `pkg update && pkg install -y nodejs && npm install -g @anthropic-ai/gemini-cli`
  - Codex: `pkg update && pkg install -y nodejs && npm install -g @openai/codex`
- **Claude Power Mode** - `--dangerously-skip-permissions` flag
- **System Prompt** - Creates ~/CLAUDE.md automatically

### 1.5 Welcome Animation
- **5-second animated overlay** before AI launch
- **Progress indicators** during installation
- **Beautiful card-based AI selection**

### 1.6 Developer Mode
- **Secret activation:** Tap version 7 times
- **Toggle ON/OFF** in drawer
- **Persistent:** Saved to SharedPreferences
- **Enables:** Install Dev Tools option

### 1.7 Power Features
- **Wake Lock** - Keeps CPU awake for long tasks
- **Claude Power Mode** - Skip permissions for automation
- Both have toggle buttons in drawer

### 1.8 Context Menus
- **Long-press context menu:**
  - Copy
  - Paste
  - Select All
  - More...
- **More Options menu:**
  - Copy All
  - Paste
  - New session
  - Kill session
  - Reset terminal
  - Change text size
  - Toggle keyboard
  - About

### 1.9 Dialogs
- `showSettingsDialog()` - Settings options
- `showSessionsDialog()` - Session list with actions
- `showHelpDialog()` - Usage instructions
- `showAboutDialog()` - App version info
- `showTextSizeDialog()` - Font size selection
- `showSessionOptions()` - Per-session actions

### 1.10 Terminal Callbacks
- `onTextChanged()` - Screen updates
- `onBell()` - Vibration feedback
- `onColorsChanged()` - Theme updates
- `onSessionFinished()` - Cleanup

---

## 2. BootstrapInstaller Features (931 lines)

### 2.1 Bootstrap Download
- **Source:** GitHub Termux releases (~50MB)
- **Progress tracking:** 0-100% with messages
- **Redirect handling:** Follows HTTP redirects
- **Timeout:** 30 seconds connect/read

### 2.2 Installation Steps
1. Prepare directories (usr, home, bin, lib, etc)
2. Download bootstrap ZIP
3. Extract files + create symlinks
4. Set permissions (chmod 755)
5. Install TermuxAm (am.apk)
6. Install 60+ API scripts
7. Configure npm
8. Set up GitHub
9. Initialize persistent memory
10. Write version file

### 2.3 Directory Structure Created
```
/data/data/com.termux/files/
├── usr/
│   ├── bin/        # Executables
│   ├── lib/        # Libraries
│   ├── etc/        # Config files
│   ├── tmp/        # Temp files
│   ├── var/        # Variable data
│   └── share/      # Shared data
└── home/
    ├── .bashrc     # Shell config
    ├── .profile    # Login profile
    ├── .termux/    # Termux config
    ├── .mobilecli/ # App memory
    └── CLAUDE.md   # AI guide
```

### 2.4 Configuration Files Created
- **~/.bashrc** - Environment, aliases, Claude power mode, PS1 prompt
- **~/.profile** - Login shell sourcing
- **~/.termux/termux.properties** - `allow-external-apps = true`
- **~/CLAUDE.md** - AI assistant guide
- **/etc/passwd, /etc/group** - User info
- **/etc/hosts, /etc/resolv.conf** - Network config
- **/etc/motd** - Welcome message

### 2.5 Environment Variables (50+)
```
HOME, PREFIX, PATH, LD_LIBRARY_PATH, TMPDIR, PWD, TERM,
COLORTERM, LANG, SHELL, USER, LOGNAME,
TERMUX_VERSION, TERMUX_APK_RELEASE, TERMUX__PREFIX,
TERMUX__HOME, TERMUX_APP_PID, TERMUX_APP__UID,
SSL_CERT_FILE, NODE_EXTRA_CA_CERTS, LD_PRELOAD, ...
```

### 2.6 Persistent Memory System
Location: `~/.mobilecli/memory/`
- **evolution_history.json** - Self-modification tracking
- **problems_solved.json** - Pattern recognition
- **capabilities.json** - What system can do
- **goals.json** - Current objectives
- **preferences.json** - User settings

### 2.7 Helper Scripts Installed
| Script | Purpose |
|--------|---------|
| `mobilecli-memory` | View/manage AI memory |
| `mobilecli-rebuild` | Self-rebuild the app |
| `mobilecli-caps` | Quick capabilities reference |
| `mobilecli-share` | File sharing via Bluetooth |
| `mobilecli-dev-mode` | Developer mode toggle |
| `install-dev-tools` | Install Java, Gradle, SDK |
| `setup-github` | Configure GitHub CLI |

### 2.8 60+ Termux API Scripts
```
termux-clipboard-get/set    termux-toast
termux-notification         termux-battery-status
termux-vibrate              termux-brightness
termux-torch                termux-volume
termux-audio-info           termux-wifi-*
termux-location             termux-camera-*
termux-media-*              termux-microphone-*
termux-tts-*                termux-telephony-*
termux-sms-*                termux-contact-list
termux-call-log             termux-sensor
termux-fingerprint          termux-dialog
termux-open-url             termux-share
... and more
```

---

## 3. SetupWizardActivity Features

### 3.1 Permission Requests (15+)
```
READ_EXTERNAL_STORAGE      WRITE_EXTERNAL_STORAGE
ACCESS_FINE_LOCATION       ACCESS_COARSE_LOCATION
CAMERA                     RECORD_AUDIO
READ_PHONE_STATE           CALL_PHONE
READ_CALL_LOG              READ_SMS
SEND_SMS                   RECEIVE_SMS
READ_CONTACTS              WRITE_CONTACTS
BODY_SENSORS               POST_NOTIFICATIONS (13+)
```

### 3.2 Overlay Permission
- **canDrawOverlays** check for browser OAuth
- Dialog explaining why needed
- Skip option available

### 3.3 Setup Flow
1. Check if setup already complete
2. Request overlay permission
3. Request regular permissions
4. Start bootstrap installation
5. Mark setup complete
6. Launch MainActivity

---

## 4. LicenseManager Features

### 4.1 Supabase Integration
- **API Base:** `https://mwxlguqukyfberyhtkmg.supabase.co/rest/v1`
- **RPC Function:** `verify_license`
- **Authentication:** JWT anon key

### 4.2 License Data
- **Device ID** - android_id or UUID fallback
- **License Key** - User's license
- **User Tier** - free, pro, team
- **Expires At** - License expiration
- **Last Verified** - Timestamp of last check

### 4.3 Offline Grace Period
- **7 days** - Can work offline if previously verified
- Falls back to cached license data
- Returns cached tier during offline

### 4.4 API Contract
```json
// Request to /rpc/verify_license
{
  "p_license_key": "xxx",
  "p_device_id": "xxx"
}

// Response
{
  "valid": true,
  "tier": "pro",
  "expires_at": "2026-12-31T23:59:59"
}
```

---

## 5. TermuxApiReceiver (Broadcast Handlers)

Handles all `com.termux.api.API_CALL` broadcasts:

### 5.1 API Categories
- **Clipboard** - get/set
- **Notifications** - show/remove
- **Device Info** - battery, volume, brightness
- **Connectivity** - WiFi, location
- **Media** - camera, microphone, player
- **Telephony** - calls, SMS, contacts
- **Sensors** - all device sensors
- **TTS** - text to speech
- **Fingerprint** - biometric
- **Dialog** - various input dialogs

---

## 6. Service Integration

### 6.1 TermuxService
- **Background service** for session persistence
- Sessions survive activity destroy
- Wake lock management
- URL file watching for OAuth

---

## Summary: What Makes v161 Work

### Critical Components
1. **DrawerLayout** - Navigation (swipe from left)
2. **TerminalView** - Terminal display
3. **Multi-session** - Up to 10 sessions
4. **BootstrapInstaller** - Complete environment setup
5. **60+ API scripts** - Phone hardware access
6. **AI installation** - Claude/Gemini/Codex
7. **Developer mode** - Hidden dev features
8. **Power features** - Wake lock, Claude power mode

### What Was Missing in v78 (MobileCLI-Primary)
- Navigation drawer
- Proper multi-session
- AI installation commands
- Developer mode
- Power toggles
- Many dialogs
- Proper context menu
- Session tabs styling
- Most of the 60+ API scripts
- Persistent memory system

---

## Build Requirements

- **Package name:** `com.termux` (required for RUNPATH)
- **targetSdkVersion:** 28 (required for exec())
- **minSdkVersion:** 21
- **Kotlin** with coroutines
- **AndroidX** libraries
