/**
 * MobileCLI Production - Application Class
 *
 * This is the main Application class that initializes when the app starts.
 * It handles:
 * - Global crash logging
 * - Directory initialization
 * - Application-wide state
 *
 * @since v162 (Production rebuild)
 */
package com.termux

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TermuxApplication : Application() {

    companion object {
        private const val TAG = "TermuxApplication"

        @Volatile
        private var instance: TermuxApplication? = null

        fun getInstance(): TermuxApplication? = instance

        /**
         * Default termux.properties content.
         * This file controls terminal behavior and settings.
         */
        private const val DEFAULT_TERMUX_PROPERTIES = """
# MobileCLI Terminal Properties
# Documentation: https://wiki.termux.com/wiki/Terminal_Settings

### Keyboard Settings ###

# Extra keys configuration (JSON format)
# Default keys: ESC, CTRL, ALT, TAB, -, /, |, HOME, UP, END, PGUP
# extra-keys = [['ESC','/','-','HOME','UP','END','PGUP'],['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]

# Back key behavior: "back" (default) or "escape"
# back-key = back

### Appearance ###

# Use fullscreen mode
# fullscreen = false

# Hide soft keyboard on startup
# hide-soft-keyboard-on-startup = false

### Terminal Settings ###

# Terminal transcript rows (scrollback buffer)
# terminal-transcript-rows = 2000

# Cursor blink rate in ms (0 = no blink)
# terminal-cursor-blink-rate = 500

# Cursor style: "block", "underline", or "bar"
# terminal-cursor-style = block

### Bell Settings ###

# Bell character behavior: "vibrate", "beep", "ignore"
# bell-character = vibrate

### URL/External App Settings ###

# Allow external apps to open URLs and files
# This must be true for Claude Code OAuth to work!
allow-external-apps = true

### Session Settings ###

# Default working directory
# default-working-directory = /data/data/com.termux/files/home

"""
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "MobileCLI Application starting")

        setupCrashHandler()
        initializeDirectories()

        Log.i(TAG, "MobileCLI Application initialized")
    }

    /**
     * Sets up a global uncaught exception handler to log crashes.
     * Crashes are logged to filesDir/crash.log for debugging.
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            try {
                val crashFile = File(filesDir, "crash.log")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                crashFile.appendText("=== Crash at $timestamp ===\n")
                crashFile.appendText("Thread: ${thread.name}\n")
                crashFile.appendText("Exception: ${throwable.message}\n")
                crashFile.appendText(throwable.stackTraceToString())
                crashFile.appendText("\n\n")
            } catch (e: Exception) {
                // Ignore - we tried our best
            }

            // Call the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Initializes the required directory structure for Termux.
     *
     * Directory structure:
     * - usr/         Main Termux prefix
     * - home/        User home directory
     * - home/.termux/    Termux config directory
     * - home/.termux/boot/   Boot scripts directory
     */
    private fun initializeDirectories() {
        try {
            val files = filesDir
            val usr = File(files, "usr")
            val home = File(files, "home")
            val termuxDir = File(home, ".termux")
            val bootDir = File(termuxDir, "boot")

            // Create all directories
            usr.mkdirs()
            home.mkdirs()
            termuxDir.mkdirs()
            bootDir.mkdirs()

            // Create default termux.properties if it doesn't exist
            val propertiesFile = File(termuxDir, "termux.properties")
            if (!propertiesFile.exists()) {
                propertiesFile.writeText(DEFAULT_TERMUX_PROPERTIES)
                Log.i(TAG, "Created default termux.properties")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize directories", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        instance = null
        Log.i(TAG, "MobileCLI Application terminated")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_COMPLETE) {
            Log.w(TAG, "Trim memory level: $level")
        }
    }
}
