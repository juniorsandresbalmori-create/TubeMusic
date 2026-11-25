package com.juniorsandresbalmori.tubemusic

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private val TAG = "TubeMusicDownloader"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Asegúrate de tener este layout básico

        // Inicializar yt-dlp
        initYtDlp()
        
        // Ejemplo de uso (puedes conectar esto a un botón o EditText en tu UI)
        // downloadAudio("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    private fun initYtDlp() {
        val assetName = "yt-dlp"
        val file = File(filesDir, assetName)
        
        if (!file.exists()) {
            try {
                assets.open(assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.setExecutable(true)
                Log.d(TAG, "yt-dlp binary copied and set executable.")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying yt-dlp", e)
            }
        } else {
            Log.d(TAG, "yt-dlp binary already exists.")
        }
    }

    fun downloadAudio(url: String) {
        // Verificar permisos antes de ejecutar (simplificado para el ejemplo)
        // En producción, usa ActivityResultContracts.RequestPermission
        
        Thread {
            try {
                val ytDlpBin = File(filesDir, "yt-dlp")
                
                // Definir dónde guardar
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC)
                val outputPath = "$downloadsDir/%(title)s.%(ext)s"
                
                // Comando: yt-dlp -x --audio-format mp3 -o [ruta] [url]
                val command = arrayOf(
                    ytDlpBin.absolutePath,
                    "-x",
                    "--audio-format", "mp3",
                    "-o", outputPath,
                    url
                )
                
                Log.d(TAG, "Executing: ${command.joinToString(" ")}")
                
                val process = ProcessBuilder(*command).start()
                
                // Leer errores para depuración
                val errorOutput = process.errorStream.bufferedReader().readText()
                if (errorOutput.isNotEmpty()) {
                    Log.w(TAG, "yt-dlp warnings/errors: $errorOutput")
                }
                
                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    Log.d(TAG, "Download successful!")
                    // Aquí puedes notificar al usuario o actualizar la UI
                    runOnUiThread {
                        // Toast.makeText(this, "Descarga completada", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Download failed with code: $exitCode")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during download", e)
            }
        }.start()
    }
                     }
