/**
 * MobileCLI Production - Main Activity
 *
 * The main terminal UI activity. This handles:
 * - Terminal display and input
 * - Session management (up to 10 sessions)
 * - Navigation drawer
 * - Extra keys row
 * - AI integration
 * - Power features (wake lock, power mode)
 * - Developer/Admin mode
 *
 * @since v162 (Production rebuild)
 */
package com.termux

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.termux.app.TermuxService
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Main terminal activity implementing terminal and session callbacks.
 */
class MainActivity : AppCompatActivity(), TerminalViewClient, TerminalSessionClient {

    companion object {
        private const val TAG = "MainActivity"

        // SharedPreferences keys
        private const val PREFS_NAME = "MobileCLIPrefs"
        private const val KEY_DEV_MODE = "developer_mode_enabled"
        private const val KEY_ADMIN_MODE = "admin_mode_enabled"
        private const val KEY_WAKE_LOCK = "wake_lock_enabled"
        private const val KEY_CLAUDE_POWER = "claude_power_mode"
        private const val KEY_TEXT_SIZE = "terminal_text_size"
        private const val KEY_SELECTED_AI = "selected_ai"

        // AI choices
        private const val AI_CLAUDE = "claude"
        private const val AI_GEMINI = "gemini"
        private const val AI_CODEX = "codex"
        private const val AI_NONE = "none"

        // Session limits
        private const val MAX_SESSIONS = 10
        private const val SESSION_TRANSCRIPT_ROWS = 2000

        // Admin mode config
        private const val ADMIN_TAP_COUNT = 7
        private const val ADMIN_TAP_TIMEOUT = 2000L
    }

    // ==========================================
    // UI Components
    // ==========================================
    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var extraKeysLayout: LinearLayout
    private lateinit var sessionTabsScroll: HorizontalScrollView
    private lateinit var sessionTabsLayout: LinearLayout
    private lateinit var sessionsListLayout: LinearLayout

    // Overlays
    private var setupOverlay: FrameLayout? = null
    private var aiChoiceScreen: ScrollView? = null
    private var setupProgress: ProgressBar? = null
    private var setupPercentage: TextView? = null
    private var setupStep: TextView? = null
    private var setupStatus: TextView? = null

    // Navigation drawer items
    private var wakeLockToggle: TextView? = null
    private var powerModeToggle: TextView? = null
    private var devModeToggle: TextView? = null
    private var devOptionsDivider: View? = null
    private var devOptionsHeader: TextView? = null
    private var installAIItem: TextView? = null
    private var installDevToolsItem: TextView? = null
    private var versionText: TextView? = null

    // ==========================================
    // State Variables
    // ==========================================
    private val sessions = mutableListOf<TerminalSession>()
    private var currentSessionIndex = 0

    private var isCtrlPressed = false
    private var isAltPressed = false
    private var isKeyboardVisible = false
    private var keyboardHeightThreshold = 0

    private var isWakeLockEnabled = false
    private var isClaudePowerEnabled = false
    private var developerModeEnabled = false
    private var adminModeEnabled = false

    private var currentTextSize = 28
    private val minTextSize = 14
    private val maxTextSize = 56

    private var selectedAI: String? = null

    // Admin mode tap detection
    private var versionTapCount = 0
    private var lastVersionTapTime = 0L

    // ==========================================
    // Service Connection
    // ==========================================
    private var termuxService: TermuxService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TermuxService.LocalBinder
            termuxService = binder.getService()
            serviceBound = true
            termuxService?.setSessionClient(this@MainActivity)
            Log.i(TAG, "Service connected")

            // Restore sessions or create initial session
            createSessionOrDefer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            termuxService = null
            serviceBound = false
            Log.i(TAG, "Service disconnected")
        }
    }

    // ==========================================
    // Bootstrap Installer
    // ==========================================
    private lateinit var bootstrapInstaller: BootstrapInstaller
    private var bootstrapReadyPendingSession = false

    // ==========================================
    // Handler for UI updates
    // ==========================================
    private val uiHandler = Handler(Looper.getMainLooper())

    // ==========================================
    // Lifecycle Methods
    // ==========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        initializeViews()

        // Start service
        startTermuxService()

        // Load saved state
        loadPreferences()

        // Setup UI handlers
        setupExtraKeys()
        setupNavDrawer()
        setupKeyboardListener()
        setupBackButtonHandler()
        setupAdminMode()

        // Check bootstrap installation
        checkBootstrap()
    }

    override fun onStart() {
        super.onStart()
        // Bind to service
        Intent(this, TermuxService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Save state
        savePreferences()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // ==========================================
    // Initialization
    // ==========================================

    private fun initializeViews() {
        // Terminal view
        terminalView = findViewById(R.id.terminal_view)
        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(currentTextSize)
        terminalView.alpha = 0f // Hidden initially

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout)

        // Extra keys
        extraKeysLayout = findViewById(R.id.extra_keys)

        // Session tabs
        sessionTabsScroll = findViewById(R.id.session_tabs_scroll)
        sessionTabsLayout = findViewById(R.id.session_tabs)

        // Sessions list in drawer
        sessionsListLayout = findViewById(R.id.sessions_list)

        // Overlays
        setupOverlay = findViewById(R.id.setup_overlay)
        aiChoiceScreen = findViewById(R.id.ai_choice_screen)
        setupProgress = setupOverlay?.findViewById(R.id.setup_progress)
        setupPercentage = setupOverlay?.findViewById(R.id.setup_percentage)
        setupStep = setupOverlay?.findViewById(R.id.setup_step)
        setupStatus = setupOverlay?.findViewById(R.id.setup_status)

        // Navigation drawer items
        wakeLockToggle = findViewById(R.id.nav_wake_lock)
        powerModeToggle = findViewById(R.id.nav_power_mode)
        devModeToggle = findViewById(R.id.nav_dev_mode)
        devOptionsDivider = findViewById(R.id.dev_options_divider)
        devOptionsHeader = findViewById(R.id.nav_dev_options_header)
        installAIItem = findViewById(R.id.nav_install_ai)
        installDevToolsItem = findViewById(R.id.nav_install_dev_tools)
        versionText = findViewById(R.id.nav_version)

        // Update version text
        versionText?.text = "v${BuildConfig.VERSION_NAME}"

        // Bootstrap installer
        bootstrapInstaller = BootstrapInstaller(this)

        // Update UI based on saved state
        updatePowerModeUI()
        updateDeveloperModeUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        developerModeEnabled = prefs.getBoolean(KEY_DEV_MODE, BuildConfig.DEV_MODE_DEFAULT)
        adminModeEnabled = prefs.getBoolean(KEY_ADMIN_MODE, false)
        isWakeLockEnabled = prefs.getBoolean(KEY_WAKE_LOCK, false)
        isClaudePowerEnabled = prefs.getBoolean(KEY_CLAUDE_POWER, false)
        currentTextSize = prefs.getInt(KEY_TEXT_SIZE, 28)
        selectedAI = prefs.getString(KEY_SELECTED_AI, null)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_DEV_MODE, developerModeEnabled)
            putBoolean(KEY_ADMIN_MODE, adminModeEnabled)
            putBoolean(KEY_WAKE_LOCK, isWakeLockEnabled)
            putBoolean(KEY_CLAUDE_POWER, isClaudePowerEnabled)
            putInt(KEY_TEXT_SIZE, currentTextSize)
            selectedAI?.let { putString(KEY_SELECTED_AI, it) }
            apply()
        }
    }

    // ==========================================
    // Service Management
    // ==========================================

    private fun startTermuxService() {
        Intent(this, TermuxService::class.java).also { intent ->
            startService(intent)
        }
    }

    // ==========================================
    // Bootstrap Management
    // ==========================================

    private fun checkBootstrap() {
        if (bootstrapInstaller.isInstalled()) {
            Log.i(TAG, "Bootstrap already installed")
            onBootstrapReady()
        } else {
            Log.i(TAG, "Bootstrap not installed, starting installation")
            showSetupOverlay()
            installBootstrap()
        }
    }

    private fun installBootstrap() {
        lifecycleScope.launch {
            try {
                bootstrapInstaller.install { progress, status ->
                    uiHandler.post {
                        updateSetupProgress(progress, status)
                    }
                }
                uiHandler.post {
                    onBootstrapReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap installation failed", e)
                uiHandler.post {
                    showError("Installation failed: ${e.message}")
                }
            }
        }
    }

    private fun onBootstrapReady() {
        hideSetupOverlay()

        // Show AI choice screen if no AI selected yet
        if (selectedAI == null) {
            showAIChoiceScreen()
        } else {
            // Show terminal
            terminalView.alpha = 1f
            bootstrapReadyPendingSession = true
            if (serviceBound && sessions.isEmpty()) {
                createSession()
            }
        }
    }

    // ==========================================
    // Setup Overlay
    // ==========================================

    private fun showSetupOverlay() {
        setupOverlay?.visibility = View.VISIBLE
        terminalView.alpha = 0f
    }

    private fun hideSetupOverlay() {
        setupOverlay?.visibility = View.GONE
        terminalView.alpha = 1f
    }

    private fun updateSetupProgress(progress: Int, status: String) {
        setupProgress?.progress = progress
        setupPercentage?.text = "$progress%"
        setupStatus?.text = status
    }

    // ==========================================
    // AI Choice Screen
    // ==========================================

    private fun showAIChoiceScreen() {
        aiChoiceScreen?.visibility = View.VISIBLE
        terminalView.alpha = 0f

        // Setup card click handlers
        aiChoiceScreen?.findViewById<MaterialCardView>(R.id.card_claude)?.setOnClickListener {
            onAISelected(AI_CLAUDE)
        }
        aiChoiceScreen?.findViewById<MaterialCardView>(R.id.card_gemini)?.setOnClickListener {
            onAISelected(AI_GEMINI)
        }
        aiChoiceScreen?.findViewById<MaterialCardView>(R.id.card_codex)?.setOnClickListener {
            onAISelected(AI_CODEX)
        }
        aiChoiceScreen?.findViewById<MaterialCardView>(R.id.card_terminal)?.setOnClickListener {
            onAISelected(AI_NONE)
        }
    }

    private fun hideAIChoiceScreen() {
        aiChoiceScreen?.visibility = View.GONE
        terminalView.alpha = 1f
    }

    private fun onAISelected(ai: String) {
        selectedAI = ai
        savePreferences()
        hideAIChoiceScreen()

        // Create session
        if (sessions.isEmpty()) {
            createSession()
        }

        // Install selected AI if not "none"
        if (ai != AI_NONE) {
            installSelectedAI(ai)
        }
    }

    private fun installSelectedAI(ai: String) {
        val command = when (ai) {
            AI_CLAUDE -> "pkg update -y && pkg upgrade -y && pkg install nodejs-lts -y && npm install -g @anthropic-ai/claude-code"
            AI_GEMINI -> "pkg update -y && pkg upgrade -y && pkg install nodejs-lts -y && npm install -g @anthropic-ai/gemini-cli"
            AI_CODEX -> "pkg update -y && pkg upgrade -y && pkg install nodejs-lts -y && npm install -g @openai/codex"
            else -> return
        }

        // Write command to current session
        getCurrentSession()?.write("$command\n")
    }

    // ==========================================
    // Session Management
    // ==========================================

    private fun getCurrentSession(): TerminalSession? {
        return sessions.getOrNull(currentSessionIndex)
    }

    private fun createSessionOrDefer() {
        if (bootstrapInstaller.isInstalled() && sessions.isEmpty()) {
            createSession()
        }
    }

    private fun createSession(): TerminalSession? {
        if (sessions.size >= MAX_SESSIONS) {
            Toast.makeText(this, getString(R.string.session_limit_reached), Toast.LENGTH_SHORT).show()
            return null
        }

        val shell = File(bootstrapInstaller.binDir, "bash")
        if (!shell.exists()) {
            Log.e(TAG, "Shell not found: ${shell.absolutePath}")
            return null
        }

        val cwd = bootstrapInstaller.homeDir
        val args = arrayOf("-l")
        val env = bootstrapInstaller.getEnvironment()

        val session = termuxService?.createSession(
            shell.absolutePath,
            cwd.absolutePath,
            args,
            env,
            this
        ) ?: TerminalSession(
            shell.absolutePath,
            cwd.absolutePath,
            args,
            env,
            SESSION_TRANSCRIPT_ROWS,
            this
        )

        sessions.add(session)
        currentSessionIndex = sessions.size - 1

        // Attach to terminal view
        terminalView.attachSession(session)

        // Update UI
        updateSessionTabs()
        updateDrawerSessionsList()

        // Show tabs if more than one session
        sessionTabsScroll.visibility = if (sessions.size > 1) View.VISIBLE else View.GONE

        Log.i(TAG, "Session created, count: ${sessions.size}")

        return session
    }

    private fun switchToSession(index: Int) {
        if (index < 0 || index >= sessions.size) return

        currentSessionIndex = index
        terminalView.attachSession(sessions[index])
        updateSessionTabs()
    }

    private fun killCurrentSession() {
        if (sessions.isEmpty()) return

        val session = sessions[currentSessionIndex]
        session.finishIfRunning()
        sessions.removeAt(currentSessionIndex)
        termuxService?.removeSession(session)

        if (sessions.isEmpty()) {
            createSession()
        } else {
            currentSessionIndex = currentSessionIndex.coerceAtMost(sessions.size - 1)
            terminalView.attachSession(sessions[currentSessionIndex])
        }

        updateSessionTabs()
        updateDrawerSessionsList()

        // Hide tabs if only one session
        sessionTabsScroll.visibility = if (sessions.size > 1) View.VISIBLE else View.GONE
    }

    private fun updateSessionTabs() {
        sessionTabsLayout.removeAllViews()

        sessions.forEachIndexed { index, _ ->
            val tab = TextView(this).apply {
                text = "Session ${index + 1}"
                setTextColor(resources.getColor(R.color.tab_text, theme))
                textSize = 12f
                setPadding(24, 16, 24, 16)
                background = resources.getDrawable(
                    if (index == currentSessionIndex) R.drawable.bg_session_tab_selected
                    else R.drawable.bg_session_tab,
                    theme
                )
                setOnClickListener { switchToSession(index) }
                setOnLongClickListener {
                    showSessionOptions(index)
                    true
                }
            }
            sessionTabsLayout.addView(tab)
        }
    }

    private fun updateDrawerSessionsList() {
        sessionsListLayout.removeAllViews()

        sessions.forEachIndexed { index, _ ->
            val item = TextView(this).apply {
                text = "Session ${index + 1}"
                setTextColor(resources.getColor(R.color.drawer_item_text, theme))
                textSize = 14f
                setPadding(32, 16, 16, 16)
                setOnClickListener {
                    switchToSession(index)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            sessionsListLayout.addView(item)
        }
    }

    private fun showSessionOptions(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Session ${index + 1}")
            .setItems(arrayOf("Switch to", "Close")) { _, which ->
                when (which) {
                    0 -> switchToSession(index)
                    1 -> {
                        currentSessionIndex = index
                        killCurrentSession()
                    }
                }
            }
            .show()
    }

    // ==========================================
    // Extra Keys
    // ==========================================

    private fun setupExtraKeys() {
        findViewById<TextView>(R.id.key_esc)?.setOnClickListener { sendSpecialKey("ESC") }
        findViewById<TextView>(R.id.key_ctrl)?.setOnClickListener { toggleCtrl() }
        findViewById<TextView>(R.id.key_alt)?.setOnClickListener { toggleAlt() }
        findViewById<TextView>(R.id.key_tab)?.setOnClickListener { sendSpecialKey("TAB") }
        findViewById<TextView>(R.id.key_dash)?.setOnClickListener { sendChar('-') }
        findViewById<TextView>(R.id.key_slash)?.setOnClickListener { sendChar('/') }
        findViewById<TextView>(R.id.key_pipe)?.setOnClickListener { sendChar('|') }
        findViewById<TextView>(R.id.key_up)?.setOnClickListener { sendSpecialKey("UP") }
        findViewById<TextView>(R.id.key_down)?.setOnClickListener { sendSpecialKey("DOWN") }
        findViewById<TextView>(R.id.key_left)?.setOnClickListener { sendSpecialKey("LEFT") }
        findViewById<TextView>(R.id.key_right)?.setOnClickListener { sendSpecialKey("RIGHT") }
    }

    private fun toggleCtrl() {
        isCtrlPressed = !isCtrlPressed
        updateModifierButtons()
    }

    private fun toggleAlt() {
        isAltPressed = !isAltPressed
        updateModifierButtons()
    }

    private fun updateModifierButtons() {
        findViewById<TextView>(R.id.key_ctrl)?.apply {
            setBackgroundColor(
                if (isCtrlPressed) resources.getColor(R.color.extra_keys_pressed, theme)
                else resources.getColor(android.R.color.transparent, theme)
            )
        }
        findViewById<TextView>(R.id.key_alt)?.apply {
            setBackgroundColor(
                if (isAltPressed) resources.getColor(R.color.extra_keys_pressed, theme)
                else resources.getColor(android.R.color.transparent, theme)
            )
        }
    }

    private fun sendChar(c: Char) {
        getCurrentSession()?.write(c.toString())
        clearModifiers()
    }

    private fun sendSpecialKey(key: String) {
        val sequence = when (key) {
            "ESC" -> "\u001b"
            "TAB" -> "\t"
            "UP" -> "\u001b[A"
            "DOWN" -> "\u001b[B"
            "LEFT" -> "\u001b[D"
            "RIGHT" -> "\u001b[C"
            else -> return
        }
        getCurrentSession()?.write(sequence)
        clearModifiers()
    }

    private fun clearModifiers() {
        isCtrlPressed = false
        isAltPressed = false
        updateModifierButtons()
    }

    // ==========================================
    // Navigation Drawer
    // ==========================================

    private fun setupNavDrawer() {
        // New Session
        findViewById<TextView>(R.id.nav_new_session)?.setOnClickListener {
            createSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Settings
        findViewById<TextView>(R.id.nav_settings)?.setOnClickListener {
            showSettingsDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Keyboard
        findViewById<TextView>(R.id.nav_keyboard)?.setOnClickListener {
            toggleKeyboard()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Text Size
        findViewById<TextView>(R.id.nav_text_size)?.setOnClickListener {
            showTextSizeDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Install AI (admin only)
        installAIItem?.setOnClickListener {
            showAIChooserDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Help
        findViewById<TextView>(R.id.nav_help)?.setOnClickListener {
            showHelpDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // About
        findViewById<TextView>(R.id.nav_about)?.setOnClickListener {
            showAboutDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Wake Lock Toggle
        wakeLockToggle?.setOnClickListener {
            toggleWakeLock()
        }

        // Power Mode Toggle
        powerModeToggle?.setOnClickListener {
            toggleClaudePowerMode()
        }

        // Developer Mode Toggle
        devModeToggle?.setOnClickListener {
            toggleDeveloperMode()
        }

        // Install Dev Tools
        installDevToolsItem?.setOnClickListener {
            runInstallDevTools()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    // ==========================================
    // Admin/Developer Mode
    // ==========================================

    private fun setupAdminMode() {
        versionText?.setOnClickListener {
            val now = System.currentTimeMillis()

            if (now - lastVersionTapTime > ADMIN_TAP_TIMEOUT) {
                versionTapCount = 0
            }

            versionTapCount++
            lastVersionTapTime = now

            val remaining = ADMIN_TAP_COUNT - versionTapCount
            if (remaining in 1..3) {
                Toast.makeText(this, getString(R.string.admin_tap_hint, remaining), Toast.LENGTH_SHORT).show()
            }

            if (versionTapCount >= ADMIN_TAP_COUNT) {
                versionTapCount = 0
                if (!adminModeEnabled) {
                    showAdminPasswordDialog()
                } else {
                    adminModeEnabled = false
                    savePreferences()
                    updateDeveloperModeUI()
                    Toast.makeText(this, getString(R.string.admin_mode_disabled), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAdminPasswordDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.admin_enter_password)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_enter_password))
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val password = input.text.toString()
                val hash = md5(password)

                if (hash == BuildConfig.ADMIN_PASSWORD_HASH) {
                    adminModeEnabled = true
                    developerModeEnabled = true
                    savePreferences()
                    updateDeveloperModeUI()
                    Toast.makeText(this, getString(R.string.admin_mode_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.admin_wrong_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun toggleDeveloperMode() {
        developerModeEnabled = !developerModeEnabled
        savePreferences()
        updateDeveloperModeUI()
    }

    private fun updateDeveloperModeUI() {
        val showDevOptions = developerModeEnabled || adminModeEnabled
        val showAdminOptions = adminModeEnabled

        devOptionsDivider?.visibility = if (showDevOptions) View.VISIBLE else View.GONE
        devOptionsHeader?.visibility = if (showDevOptions) View.VISIBLE else View.GONE
        devModeToggle?.visibility = if (showDevOptions) View.VISIBLE else View.GONE
        devModeToggle?.text = "Developer Mode: ${if (developerModeEnabled) "ON" else "OFF"}"

        installDevToolsItem?.visibility = if (showDevOptions) View.VISIBLE else View.GONE
        installAIItem?.visibility = if (showAdminOptions) View.VISIBLE else View.GONE
    }

    private fun updatePowerModeUI() {
        wakeLockToggle?.text = "Wake Lock: ${if (isWakeLockEnabled) "ON" else "OFF"}"
        powerModeToggle?.text = "Power Mode: ${if (isClaudePowerEnabled) "ON" else "OFF"}"
    }

    // ==========================================
    // Power Features
    // ==========================================

    private fun toggleWakeLock() {
        isWakeLockEnabled = !isWakeLockEnabled
        savePreferences()

        if (isWakeLockEnabled) {
            termuxService?.acquireWakeLock()
            Toast.makeText(this, getString(R.string.wake_lock_acquired), Toast.LENGTH_SHORT).show()
        } else {
            termuxService?.releaseWakeLock()
            Toast.makeText(this, getString(R.string.wake_lock_released), Toast.LENGTH_SHORT).show()
        }

        updatePowerModeUI()
    }

    private fun toggleClaudePowerMode() {
        isClaudePowerEnabled = !isClaudePowerEnabled
        savePreferences()

        val message = if (isClaudePowerEnabled) {
            getString(R.string.power_mode_enabled)
        } else {
            getString(R.string.power_mode_disabled)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        updatePowerModeUI()
    }

    private fun runInstallDevTools() {
        val command = """
            echo "Installing development tools..."
            pkg update -y && pkg upgrade -y
            pkg install -y openjdk-17 gradle git
            echo "Development tools installed!"
        """.trimIndent()

        getCurrentSession()?.write("$command\n")
    }

    // ==========================================
    // Dialogs
    // ==========================================

    private fun showSettingsDialog() {
        // TODO: Implement settings activity
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun showTextSizeDialog() {
        val sizes = arrayOf("Small (14)", "Medium (20)", "Default (28)", "Large (36)", "X-Large (48)")
        val values = arrayOf(14, 20, 28, 36, 48)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_text_size))
            .setItems(sizes) { _, which ->
                currentTextSize = values[which]
                terminalView.setTextSize(currentTextSize)
                savePreferences()
            }
            .show()
    }

    private fun showAIChooserDialog() {
        val ais = arrayOf("Claude (Recommended)", "Gemini", "Codex")
        val values = arrayOf(AI_CLAUDE, AI_GEMINI, AI_CODEX)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_install_ai))
            .setItems(ais) { _, which ->
                installSelectedAI(values[which])
            }
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.help_title))
            .setMessage("""
                MobileCLI - AI Terminal for Android

                Navigation:
                - Swipe from left edge for menu
                - Use extra keys row for special keys

                Commands:
                - termux-clipboard-get: Read clipboard
                - termux-toast "message": Show toast
                - termux-vibrate: Vibrate phone

                For more help, visit the documentation.
            """.trimIndent())
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage("""
                ${getString(R.string.about_version, BuildConfig.VERSION_NAME)}

                ${getString(R.string.about_description)}

                ${getString(R.string.about_copyright)}
            """.trimIndent())
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_generic))
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    // ==========================================
    // Keyboard Handling
    // ==========================================

    private fun setupKeyboardListener() {
        val rootView = window.decorView.rootView
        keyboardHeightThreshold = resources.displayMetrics.heightPixels / 4

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keyboardHeight = screenHeight - rect.bottom

            val wasVisible = isKeyboardVisible
            isKeyboardVisible = keyboardHeight > keyboardHeightThreshold

            if (wasVisible != isKeyboardVisible) {
                // Keyboard visibility changed
            }
        }
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isKeyboardVisible) {
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        } else {
            terminalView.requestFocus()
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (isKeyboardVisible) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    // ==========================================
    // TerminalViewClient Implementation
    // ==========================================

    override fun onScale(scale: Float): Float {
        val newSize = (currentTextSize * scale).toInt().coerceIn(minTextSize, maxTextSize)
        if (newSize != currentTextSize) {
            currentTextSize = newSize
            terminalView.setTextSize(currentTextSize)
            savePreferences()
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent?) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

    override fun onLongPress(event: MotionEvent?): Boolean {
        showContextMenu()
        return true
    }

    override fun readControlKey(): Boolean = isCtrlPressed

    override fun readAltKey(): Boolean = isAltPressed

    override fun readFnKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() {
        terminalView.setTerminalCursorBlinkerState(true, true)
    }

    // ==========================================
    // TerminalSessionClient Implementation
    // ==========================================

    override fun onTextChanged(changedSession: TerminalSession?) {
        if (changedSession == getCurrentSession()) {
            terminalView.onScreenUpdated()
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession?) {}

    override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {
        if (!text.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MobileCLI", text))
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            session?.write(text)
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession?) {
        if (finishedSession != null && sessions.contains(finishedSession)) {
            val index = sessions.indexOf(finishedSession)
            sessions.remove(finishedSession)
            termuxService?.removeSession(finishedSession)

            if (sessions.isEmpty()) {
                createSession()
            } else {
                currentSessionIndex = currentSessionIndex.coerceAtMost(sessions.size - 1)
                terminalView.attachSession(sessions[currentSessionIndex])
            }

            updateSessionTabs()
            updateDrawerSessionsList()
            sessionTabsScroll.visibility = if (sessions.size > 1) View.VISIBLE else View.GONE
        }
    }

    override fun onBell(session: TerminalSession?) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onColorsChanged(changedSession: TerminalSession?) {
        if (changedSession == getCurrentSession()) {
            terminalView.onScreenUpdated()
        }
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int = TerminalEmulatorConstants.TERMINAL_CURSOR_STYLE_BLOCK

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: TAG, message ?: "Unknown error")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: TAG, message ?: "Unknown warning")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: TAG, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: TAG, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: TAG, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message, e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: TAG, "Exception", e)
    }

    // ==========================================
    // Context Menu
    // ==========================================

    private fun showContextMenu() {
        val items = arrayOf(
            getString(R.string.menu_copy),
            getString(R.string.menu_paste),
            getString(R.string.menu_select_all),
            getString(R.string.menu_more)
        )

        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> copyToClipboard()
                    1 -> pasteFromClipboard()
                    2 -> selectAll()
                    3 -> showMoreOptions()
                }
            }
            .show()
    }

    private fun showMoreOptions() {
        val items = arrayOf(
            getString(R.string.menu_copy_all),
            getString(R.string.menu_new_session),
            getString(R.string.menu_kill_session),
            getString(R.string.menu_reset_terminal),
            getString(R.string.menu_change_text_size),
            getString(R.string.menu_toggle_keyboard)
        )

        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> copyAllToClipboard()
                    1 -> createSession()
                    2 -> killCurrentSession()
                    3 -> resetTerminal()
                    4 -> showTextSizeDialog()
                    5 -> toggleKeyboard()
                }
            }
            .show()
    }

    private fun copyToClipboard() {
        // Try to get selected text from terminal view
        val text = try {
            val field = terminalView.javaClass.getDeclaredField("mTextSelectionCursorController")
            field.isAccessible = true
            val controller = field.get(terminalView)
            if (controller != null) {
                val method = controller.javaClass.getDeclaredMethod("getSelectedText")
                method.isAccessible = true
                method.invoke(controller) as? String
            } else null
        } catch (e: Exception) {
            null
        }

        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MobileCLI", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllToClipboard() {
        val session = getCurrentSession() ?: return
        val transcript = session.emulator?.screen?.getTranscriptText() ?: return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MobileCLI", transcript))
        Toast.makeText(this, "Copied all text", Toast.LENGTH_SHORT).show()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return

        getCurrentSession()?.write(text)
    }

    private fun selectAll() {
        // TODO: Implement select all
        Toast.makeText(this, "Select All - Coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun resetTerminal() {
        getCurrentSession()?.reset()
        terminalView.onScreenUpdated()
        Toast.makeText(this, "Terminal reset", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Constants for terminal emulator cursor styles.
 */
object TerminalEmulatorConstants {
    const val TERMINAL_CURSOR_STYLE_BLOCK = 0
    const val TERMINAL_CURSOR_STYLE_UNDERLINE = 1
    const val TERMINAL_CURSOR_STYLE_BAR = 2
}
