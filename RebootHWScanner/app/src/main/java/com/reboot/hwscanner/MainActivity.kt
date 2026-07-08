package com.reboot.hwscanner

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.RandomAccessFile
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    private lateinit var specsText: TextView
    private var gpuRenderer: String = "Unknown"
    private var gpuVendor: String = "Unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        specsText = findViewById(R.id.specsText)
        val refreshButton: Button = findViewById(R.id.refreshButton)
        val copyButton: Button = findViewById(R.id.copyButton)
        val glSurface: GLSurfaceView = findViewById(R.id.glSurface)

        glSurface.setEGLContextClientVersion(2)
        glSurface.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
                gpuRenderer = gl.glGetString(GL10.GL_RENDERER) ?: "Unknown"
                gpuVendor = gl.glGetString(GL10.GL_VENDOR) ?: "Unknown"
                runOnUiThread { specsText.text = buildReport() }
            }
            override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {}
            override fun onDrawFrame(gl: GL10) {}
        })
        glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        glSurface.requestRender()

        specsText.text = buildReport()

        refreshButton.setOnClickListener {
            specsText.text = buildReport()
            glSurface.requestRender()
        }

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("RE:BOOT specs", specsText.text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    /* ================================================
       BUILD FULL REPORT
       ================================================ */
    private fun buildReport(): String {
        val sb = StringBuilder()

        sb.append("=== DEVICE ===\n")
        sb.append("Manufacturer : ${Build.MANUFACTURER}\n")
        sb.append("Brand        : ${Build.BRAND}\n")
        sb.append("Model        : ${Build.MODEL}\n")
        sb.append("Device       : ${Build.DEVICE}\n")
        sb.append("Board        : ${Build.BOARD}\n")   // closest phone equivalent to "motherboard"
        sb.append("Hardware     : ${Build.HARDWARE}\n")
        sb.append("\n")

        sb.append("=== OS ===\n")
        sb.append("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Build ID     : ${Build.DISPLAY}\n")
        sb.append("\n")

        sb.append("=== CPU ===\n")
        sb.append("Cores        : ${Runtime.getRuntime().availableProcessors()}\n")
        sb.append("ABIs         : ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
        val cpuInfo = readCpuInfo()
        if (cpuInfo.isNotEmpty()) sb.append(cpuInfo)
        sb.append("\n")

        sb.append("=== GPU ===\n")
        sb.append("Renderer     : $gpuRenderer\n")
        sb.append("Vendor       : $gpuVendor\n")
        sb.append("\n")

        sb.append("=== MEMORY ===\n")
        sb.append(readMemInfo())
        sb.append("\n")

        sb.append("=== STORAGE ===\n")
        sb.append(readStorageInfo())

        return sb.toString()
    }

    /* ================================================
       CPU DETAIL FROM /proc/cpuinfo (best-effort;
       some fields may be blocked on newer Android
       versions, hence the try/catch fallback)
       ================================================ */
    private fun readCpuInfo(): String {
        return try {
            val file = RandomAccessFile("/proc/cpuinfo", "r")
            val wanted = listOf("Hardware", "model name", "Processor", "vendor_id")
            val found = LinkedHashMap<String, String>()

            var line: String?
            while (file.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                val parts = currentLine.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    if (wanted.contains(key) && !found.containsKey(key)) {
                        found[key] = parts[1].trim()
                    }
                }
            }
            file.close()

            if (found.isEmpty()) "" else found.entries.joinToString("\n") { "${it.key.padEnd(12)} : ${it.value}" } + "\n"
        } catch (e: Exception) {
            "" // silently skip if /proc/cpuinfo isn't readable on this device
        }
    }

    /* ================================================
       RAM VIA ACTIVITY MANAGER
       ================================================ */
    private fun readMemInfo(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)

        return "Total RAM   : %.2f GB\n".format(totalGb) +
               "Available   : %.2f GB\n".format(availGb) +
               "Low Memory  : ${memInfo.lowMemory}\n"
    }

    /* ================================================
       STORAGE VIA STATFS
       ================================================ */
    private fun readStorageInfo(): String {
        val path = File("/data")
        val stat = StatFs(path.path)

        val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)

        return "Total       : %.2f GB\n".format(totalGb) +
               "Free        : %.2f GB\n".format(freeGb)
    }
}
