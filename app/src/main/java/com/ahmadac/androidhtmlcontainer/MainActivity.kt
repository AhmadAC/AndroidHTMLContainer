package com.ahmadac.androidhtmlcontainer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webRootDirectory: File
    private var activeWebView: WebView? = null

    private val runtimePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // We request the permissions on launch but proceed to let the user select a folder
        }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                showLoadingScreen()
                thread {
                    try {
                        if (webRootDirectory.exists()) {
                            webRootDirectory.deleteRecursively()
                        }
                        webRootDirectory.mkdirs()

                        val selectedFolder = DocumentFile.fromTreeUri(this, uri)
                        if (selectedFolder != null) {
                            copyDirectoryContents(selectedFolder, webRootDirectory)
                        }

                        runOnUiThread {
                            val checkIndex = File(webRootDirectory, "index.html")
                            if (checkIndex.exists()) {
                                Toast.makeText(this, "Files imported. Restarting App...", Toast.LENGTH_SHORT).show()
                                recreate()
                            } else {
                                Toast.makeText(this, "Warning: 'index.html' not found in root folder.", Toast.LENGTH_LONG).show()
                                recreate()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "Error copying files: ${e.message}", Toast.LENGTH_LONG).show()
                            recreate()
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        webRootDirectory = File(filesDir, "webroot")
        val indexFile = File(webRootDirectory, "index.html")

        if (indexFile.exists()) {
            setupWebView(indexFile)
            setupBackButtonHandler()
        } else {
            setupWelcomeScreen()
            requestAppPermissions()
        }
    }

    private fun requestAppPermissions() {
        val missingPermissions = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(missingPermissions)
        }
    }

    private fun setupWelcomeScreen() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val infoText = TextView(this).apply {
            text = "Welcome to the HTML Container App!\n\n" +
                    "To begin, please select a local directory on your device containing your HTML, CSS, and JS files. " +
                    "The folder must contain an 'index.html' file to serve as the entry point.\n\n" +
                    "To change your files later, go to your Android System Settings -> Apps -> " +
                    "this App -> Storage and click 'Clear Data' or 'Clear Cache'."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val selectFolderButton = Button(this).apply {
            text = "Choose Web Folder"
            setOnClickListener {
                folderPickerLauncher.launch(null)
            }
        }

        rootLayout.addView(infoText)
        rootLayout.addView(selectFolderButton)
        setContentView(rootLayout)
    }

    private fun showLoadingScreen() {
        val loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val progressText = TextView(this).apply {
            text = "Copying web files to internal storage...\nPlease wait."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        loadingLayout.addView(progressText)
        setContentView(loadingLayout)
    }

    private fun copyDirectoryContents(sourceDocDir: DocumentFile, destinationDir: File) {
        val files = sourceDocDir.listFiles()
        for (file in files) {
            val name = file.name ?: continue
            if (file.isDirectory) {
                val newSubDir = File(destinationDir, name)
                if (!newSubDir.exists()) {
                    newSubDir.mkdirs()
                }
                copyDirectoryContents(file, newSubDir)
            } else {
                val destinationFile = File(destinationDir, name)
                contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(indexFile: File) {
        val webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                
                // Allow XMLHttpRequest/Fetch API requests to load local resources
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true

                mediaPlaybackRequiresUserGesture = false
                setGeolocationEnabled(true)
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // Grant WebView access to requested media features (Camera, Mic) if approved in OS
                    request.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback
                ) {
                    callback.invoke(origin, true, false)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return false
                    // Keep local file navigation internal to the WebView, open external sites in system browser
                    if (url.scheme == "http" || url.scheme == "https") {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        startActivity(intent)
                        return true
                    }
                    return false
                }
            }
        }

        activeWebView = webView
        setContentView(webView)
        webView.loadUrl("file://${indexFile.absolutePath}")
    }

    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = activeWebView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
}
