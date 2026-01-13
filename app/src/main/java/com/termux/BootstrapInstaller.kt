/**
 * MobileCLI Production - Bootstrap Installer
 *
 * Downloads and installs the Termux bootstrap environment.
 * This includes:
 * - Core binaries (bash, apt, etc.)
 * - 60+ Termux API scripts
 * - Helper scripts (mobilecli-*, install-dev-tools)
 * - Configuration files (.bashrc, CLAUDE.md)
 * - Persistent memory system
 *
 * @since v162 (Production rebuild)
 */
package com.termux

import android.content.Context
import android.os.Process
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class BootstrapInstaller(private val context: Context) {

    companion object {
        private const val TAG = "BootstrapInstaller"

        // Bootstrap download URL - aarch64 Android bootstrap
        private const val BOOTSTRAP_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.01.04-r1%2Bapt.android-7/bootstrap-aarch64.zip"

        // Version tracking
        private const val BOOTSTRAP_VERSION = "mobilecli-v162"

        // Symlink prefix in SYMLINKS.txt
        private const val SYMLINK_PREFIX = "SYMLINK:"
    }

    // ==========================================
    // Directory Getters
    // ==========================================

    val filesDir: File
        get() = context.filesDir

    val prefixDir: File
        get() = File(filesDir, "usr")

    val homeDir: File
        get() = File(filesDir, "home")

    val binDir: File
        get() = File(prefixDir, "bin")

    val libDir: File
        get() = File(prefixDir, "lib")

    val etcDir: File
        get() = File(prefixDir, "etc")

    val tmpDir: File
        get() = File(prefixDir, "tmp")

    val bashPath: String
        get() = File(binDir, "bash").absolutePath

    val loginPath: String
        get() = File(binDir, "login").absolutePath

    fun getBinDir(): File = binDir
    fun getHomeDir(): File = homeDir
    fun getPrefixDir(): File = prefixDir

    // ==========================================
    // Installation Check
    // ==========================================

    /**
     * Checks if the bootstrap is installed and matches current version.
     */
    fun isInstalled(): Boolean {
        val bash = File(binDir, "bash")
        val apt = File(binDir, "apt")
        val versionFile = File(prefixDir, ".mobilecli_version")

        if (!bash.exists() || !apt.exists() || !versionFile.exists()) {
            return false
        }

        return try {
            val installedVersion = versionFile.readText().trim()
            installedVersion == BOOTSTRAP_VERSION
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // Installation Process
    // ==========================================

    /**
     * Installs the bootstrap environment.
     *
     * @param onProgress Callback for progress updates (progress: 0-100, status: String)
     */
    suspend fun install(onProgress: (Int, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled()) {
            Log.i(TAG, "Bootstrap already installed")
            return@withContext true
        }

        try {
            onProgress(0, "Preparing directories...")
            prepareDirectories()

            onProgress(5, "Downloading bootstrap (~50MB)...")
            val zipFile = downloadBootstrap(onProgress)

            onProgress(50, "Extracting files...")
            extractBootstrap(zipFile, onProgress)

            onProgress(88, "Setting permissions...")
            setPermissions()

            onProgress(90, "Installing TermuxAm...")
            installTermuxAm()

            onProgress(92, "Installing API scripts...")
            installApiScripts()

            onProgress(94, "Configuring npm...")
            createNpmConfig()

            onProgress(95, "Setting up GitHub...")
            createGitHubConfig()

            onProgress(96, "Initializing AI memory...")
            initializePersistentMemory()

            onProgress(97, "Creating configuration...")
            createBashrc()
            createClaudeMd()

            onProgress(98, "Finalizing...")
            File(prefixDir, ".mobilecli_version").writeText(BOOTSTRAP_VERSION)

            // Cleanup
            zipFile.delete()

            onProgress(100, "Complete!")
            Log.i(TAG, "Bootstrap installed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
            false
        }
    }

    // ==========================================
    // Directory Preparation
    // ==========================================

    private fun prepareDirectories() {
        listOf(
            prefixDir,
            binDir,
            libDir,
            etcDir,
            tmpDir,
            homeDir,
            File(homeDir, ".termux"),
            File(homeDir, ".mobilecli"),
            File(homeDir, ".mobilecli/memory"),
            File(prefixDir, "var/log"),
            File(prefixDir, "share"),
            File(prefixDir, "include")
        ).forEach { it.mkdirs() }
    }

    // ==========================================
    // Bootstrap Download
    // ==========================================

    private fun downloadBootstrap(onProgress: (Int, String) -> Unit): File {
        val zipFile = File(filesDir, "bootstrap.zip")

        var url = BOOTSTRAP_URL
        var connection: HttpURLConnection? = null

        try {
            // Follow redirects
            var redirectCount = 0
            while (redirectCount < 5) {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    url = connection.getHeaderField("Location")
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            val totalSize = connection!!.contentLength.toLong()
            var downloadedSize = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead

                        if (totalSize > 0) {
                            val progress = 5 + ((downloadedSize * 45) / totalSize).toInt()
                            onProgress(progress, "Downloading: ${downloadedSize / 1024}KB / ${totalSize / 1024}KB")
                        }
                    }
                }
            }

            Log.i(TAG, "Bootstrap downloaded: ${zipFile.length()} bytes")
            return zipFile
        } finally {
            connection?.disconnect()
        }
    }

    // ==========================================
    // Bootstrap Extraction
    // ==========================================

    private fun extractBootstrap(zipFile: File, onProgress: (Int, String) -> Unit) {
        val symlinksList = mutableListOf<Pair<String, String>>()

        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val name = entry.name

                if (name == "SYMLINKS.txt") {
                    // Read symlinks file
                    val content = zip.bufferedReader().readText()
                    content.lines().forEach { line ->
                        if (line.contains("←")) {
                            val parts = line.split("←")
                            if (parts.size == 2) {
                                symlinksList.add(parts[0].trim() to parts[1].trim())
                            }
                        }
                    }
                } else if (!entry.isDirectory) {
                    val outFile = File(prefixDir, name)
                    outFile.parentFile?.mkdirs()

                    FileOutputStream(outFile).use { output ->
                        zip.copyTo(output)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // Create symlinks
        onProgress(60, "Creating symlinks...")
        createSymlinks(symlinksList)
    }

    private fun createSymlinks(symlinks: List<Pair<String, String>>) {
        symlinks.forEach { (link, target) ->
            try {
                val linkFile = File(prefixDir, link)
                val targetPath = target

                linkFile.parentFile?.mkdirs()

                // Remove existing file/link
                if (linkFile.exists()) {
                    linkFile.delete()
                }

                Os.symlink(targetPath, linkFile.absolutePath)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create symlink: $link -> $target", e)
            }
        }
    }

    // ==========================================
    // Permissions
    // ==========================================

    private fun setPermissions() {
        try {
            // Set execute permission on all binaries
            Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "-R", "755", binDir.absolutePath)).waitFor()
            Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "-R", "755", libDir.absolutePath)).waitFor()

            // Specific permissions for key binaries
            listOf("bash", "sh", "apt", "dpkg", "pkg").forEach { binary ->
                val file = File(binDir, binary)
                if (file.exists()) {
                    Os.chmod(file.absolutePath, 493) // 0755
                }
            }

            Log.i(TAG, "Permissions set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting permissions", e)
        }
    }

    // ==========================================
    // TermuxAm Installation
    // ==========================================

    private fun installTermuxAm() {
        try {
            // Copy termux-am from assets
            context.assets.open("termux-am/am.apk").use { input ->
                val amDir = File(libDir, "apk")
                amDir.mkdirs()
                val amFile = File(amDir, "termux-am.apk")

                FileOutputStream(amFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Create am wrapper script
            val amScript = File(binDir, "am")
            amScript.writeText("""
                #!/data/data/com.termux/files/usr/bin/bash
                exec dalvikvm -Xcompiler-option --compiler-filter=speed -classpath /data/data/com.termux/files/usr/lib/apk/termux-am.apk com.termux.am.Am "$@"
            """.trimIndent())
            Os.chmod(amScript.absolutePath, 493)

            Log.i(TAG, "TermuxAm installed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install TermuxAm", e)
        }
    }

    // ==========================================
    // API Scripts Installation
    // ==========================================

    private fun installApiScripts() {
        val scripts = listOf(
            "termux-clipboard-get", "termux-clipboard-set",
            "termux-toast", "termux-notification", "termux-notification-remove",
            "termux-vibrate", "termux-brightness", "termux-volume",
            "termux-battery-status", "termux-wifi-connectioninfo", "termux-wifi-scaninfo",
            "termux-location", "termux-sensor",
            "termux-camera-info", "termux-camera-photo",
            "termux-microphone-record", "termux-media-player", "termux-media-scan",
            "termux-tts-speak", "termux-tts-engines",
            "termux-telephony-deviceinfo", "termux-telephony-cellinfo",
            "termux-sms-list", "termux-sms-send", "termux-sms-inbox",
            "termux-contact-list", "termux-call-log",
            "termux-fingerprint", "termux-dialog",
            "termux-download", "termux-open", "termux-open-url", "termux-share",
            "termux-torch", "termux-wallpaper",
            "termux-audio-info", "termux-usb",
            "termux-wake-lock", "termux-wake-unlock",
            "termux-job-scheduler", "termux-storage-get"
        )

        scripts.forEach { scriptName ->
            try {
                context.assets.open("scripts/$scriptName").use { input ->
                    val scriptFile = File(binDir, scriptName)
                    FileOutputStream(scriptFile).use { output ->
                        input.copyTo(output)
                    }
                    Os.chmod(scriptFile.absolutePath, 493)
                }
            } catch (e: Exception) {
                // Script not in assets, create a placeholder
                createApiScriptPlaceholder(scriptName)
            }
        }

        // Install helper scripts
        installHelperScripts()

        Log.i(TAG, "API scripts installed")
    }

    private fun createApiScriptPlaceholder(name: String) {
        val script = File(binDir, name)
        script.writeText("""
            #!/data/data/com.termux/files/usr/bin/bash
            # $name - Termux API command
            # Sends a broadcast to the Termux API receiver

            export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
            export PATH=/data/data/com.termux/files/usr/bin:${'$'}PATH

            am broadcast --user 0 -a com.termux.api.API_CALL \
                -n com.termux/.TermuxApiReceiver \
                --es api_method "$name" \
                "$@"
        """.trimIndent())
        Os.chmod(script.absolutePath, 493)
    }

    private fun installHelperScripts() {
        val helperScripts = mapOf(
            "mobilecli-memory" to """
                #!/data/data/com.termux/files/usr/bin/bash
                # MobileCLI AI Memory System

                MEMORY_DIR="${'$'}HOME/.mobilecli/memory"

                case "${'$'}1" in
                    show|view)
                        echo "=== MobileCLI Memory ==="
                        for f in "${'$'}MEMORY_DIR"/*.json; do
                            [ -f "${'$'}f" ] && echo "$(basename ${'$'}f): $(cat ${'$'}f)"
                        done
                        ;;
                    clear)
                        echo "Clearing memory..."
                        rm -f "${'$'}MEMORY_DIR"/*.json
                        echo "Memory cleared."
                        ;;
                    *)
                        echo "Usage: mobilecli-memory [show|clear]"
                        ;;
                esac
            """.trimIndent(),

            "mobilecli-caps" to """
                #!/data/data/com.termux/files/usr/bin/bash
                # MobileCLI Capabilities Reference

                echo "=== MobileCLI Capabilities ==="
                echo ""
                echo "Termux API Commands:"
                echo "  termux-clipboard-get/set  - Clipboard access"
                echo "  termux-toast \"message\"    - Show toast"
                echo "  termux-notification       - Send notification"
                echo "  termux-vibrate            - Vibrate phone"
                echo "  termux-camera-photo       - Take photo"
                echo "  termux-location           - Get GPS location"
                echo "  termux-battery-status     - Battery info"
                echo "  termux-wifi-connectioninfo - WiFi info"
                echo ""
                echo "Development Tools:"
                echo "  openjdk-17, gradle, git"
                echo ""
                echo "Storage:"
                echo "  ~/              - Home directory"
                echo "  /sdcard/Download/ - User downloads"
            """.trimIndent(),

            "install-dev-tools" to """
                #!/data/data/com.termux/files/usr/bin/bash
                # Install development tools for MobileCLI

                echo "Installing development tools..."
                pkg update -y
                pkg upgrade -y
                pkg install -y openjdk-17 gradle git

                echo ""
                echo "Development tools installed!"
                echo "  - Java 17 (OpenJDK)"
                echo "  - Gradle"
                echo "  - Git"
            """.trimIndent()
        )

        helperScripts.forEach { (name, content) ->
            val script = File(binDir, name)
            script.writeText(content)
            Os.chmod(script.absolutePath, 493)
        }
    }

    // ==========================================
    // Configuration Files
    // ==========================================

    private fun createNpmConfig() {
        val npmrc = File(homeDir, ".npmrc")
        npmrc.writeText("""
            prefix=/data/data/com.termux/files/usr
            cache=/data/data/com.termux/files/home/.npm-cache
            init-module=/data/data/com.termux/files/home/.npm-init.js
        """.trimIndent())
    }

    private fun createGitHubConfig() {
        val gitconfig = File(homeDir, ".gitconfig")
        if (!gitconfig.exists()) {
            gitconfig.writeText("""
                [user]
                    name = MobileCLI User
                    email = user@mobilecli.app
                [core]
                    editor = nano
                [credential]
                    helper = store
            """.trimIndent())
        }
    }

    private fun createBashrc() {
        val bashrc = File(homeDir, ".bashrc")
        bashrc.writeText("""
            # MobileCLI .bashrc
            # Auto-generated by BootstrapInstaller

            # Environment
            export HOME=/data/data/com.termux/files/home
            export PREFIX=/data/data/com.termux/files/usr
            export PATH=${'$'}PREFIX/bin:${'$'}PATH
            export LD_LIBRARY_PATH=${'$'}PREFIX/lib
            export TERMUX_VERSION=0.118.0
            export LANG=en_US.UTF-8

            # Claude Power Mode (if enabled)
            if [ -f "${'$'}HOME/.claude_power_mode" ]; then
                alias claude='claude --dangerously-skip-permissions'
            fi

            # Prompt
            PS1='\[\e[32m\]\u@mobilecli\[\e[0m\]:\[\e[34m\]\w\[\e[0m\]$ '

            # Aliases
            alias ll='ls -la'
            alias cls='clear'
            alias home='cd ${'$'}HOME'

            # Welcome message
            echo "Welcome to MobileCLI v${BOOTSTRAP_VERSION.removePrefix("mobilecli-v")}"
            echo "Type 'mobilecli-caps' for available commands"
        """.trimIndent())
    }

    private fun createClaudeMd() {
        val claudeMd = File(homeDir, "CLAUDE.md")
        claudeMd.writeText("""
            # MobileCLI - AI Assistant Guide

            You are running inside MobileCLI on an Android phone.

            ## File Access

            | Path | Use For |
            |------|---------|
            | ~/   | Your working directory |
            | /sdcard/Download/ | **Save files here for user to access** |
            | /sdcard/DCIM/ | Photos |
            | /sdcard/Documents/ | Documents |

            ## Available Commands

            ### Termux API (50+ commands)
            | Command | Description |
            |---------|-------------|
            | termux-clipboard-get | Read clipboard |
            | termux-clipboard-set | Write clipboard |
            | termux-toast "msg" | Show toast |
            | termux-notification -t "title" -c "text" | Send notification |
            | termux-open-url URL | Open browser |
            | termux-vibrate | Vibrate phone |
            | termux-camera-photo path | Take photo |
            | termux-battery-status | Battery info |
            | termux-wifi-connectioninfo | WiFi info |
            | termux-tts-speak "text" | Text to speech |
            | termux-wake-lock | Keep CPU awake |

            ## Quick Examples

            **Save file for user:**
            ```bash
            echo "Hello" > /sdcard/Download/hello.txt
            ```

            **Open URL:**
            ```bash
            termux-open-url "https://google.com"
            ```

            ## Tips

            1. Save user files to /sdcard/Download/
            2. Use pkg install to add packages
            3. Use termux-wake-lock for long tasks
        """.trimIndent())
    }

    // ==========================================
    // Persistent Memory
    // ==========================================

    private fun initializePersistentMemory() {
        val memoryDir = File(homeDir, ".mobilecli/memory")
        memoryDir.mkdirs()

        // Initialize memory files
        mapOf(
            "evolution_history.json" to "[]",
            "problems_solved.json" to "[]",
            "capabilities.json" to """{"terminal":true,"api":true,"development":true}""",
            "goals.json" to "[]",
            "preferences.json" to "{}"
        ).forEach { (name, content) ->
            val file = File(memoryDir, name)
            if (!file.exists()) {
                file.writeText(content)
            }
        }
    }

    // ==========================================
    // Environment Variables
    // ==========================================

    /**
     * Returns the environment variables array for creating terminal sessions.
     */
    fun getEnvironment(): Array<String> {
        val uid = Process.myUid()
        val home = homeDir.absolutePath
        val prefix = prefixDir.absolutePath
        val path = "$prefix/bin"
        val ldLibraryPath = "$prefix/lib"
        val tmpdir = "$prefix/tmp"

        return arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$path",
            "LD_LIBRARY_PATH=$ldLibraryPath",
            "TMPDIR=$tmpdir",
            "PWD=$home",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=en_US.UTF-8",
            "SHELL=$prefix/bin/bash",
            "USER=u0_a${uid % 100000}",
            "LOGNAME=u0_a${uid % 100000}",
            "TERMUX_VERSION=0.118.0",
            "TERMUX_APK_RELEASE=GITHUB",
            "TERMUX_IS_DEBUGGABLE_BUILD=0",
            "TERMUX__PREFIX=$prefix",
            "TERMUX__HOME=$home",
            "TERMUX_APP_PID=${Process.myPid()}",
            "SSL_CERT_FILE=$prefix/etc/tls/cert.pem",
            "NODE_EXTRA_CA_CERTS=$prefix/etc/tls/cert.pem"
        )
    }
}
