package com.smalegon.scanpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.smalegon.scanpdf.databinding.ActivityMainBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla única de la app:
 * 1) Escanea hojas con la cámara (usa el escáner de documentos de Google ML Kit,
 *    que detecta bordes, recorta y arma un PDF automáticamente).
 * 2) Deja elegir una carpeta del teléfono para guardar el PDF (Storage Access Framework).
 * 3) Deja compartir el PDF por cualquier app instalada (WhatsApp, correo, etc.).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Archivo PDF que acabamos de escanear (copia privada de la app, lista para guardar/compartir)
    private var currentPdfFile: File? = null

    // Carpeta elegida por el usuario para guardar los PDF (persistida entre sesiones)
    private var savedFolderUri: Uri? = null

    // true mientras esperamos que el usuario elija carpeta como parte de "Guardar y compartir"
    // (para, apenas la elija, seguir con el guardado + compartir en vez de quedarnos a medias)
    private var pendingSaveAndShare = false

    private val prefs by lazy { getSharedPreferences("scanpdf_prefs", MODE_PRIVATE) }

    // --- Lanzador que abre la pantalla de escaneo de Google y recibe el resultado ---
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val pdf = result?.pdf
            if (pdf != null) {
                val savedFile = copyScannedPdfToAppStorage(pdf.uri)
                if (savedFile != null) {
                    currentPdfFile = savedFile
                    binding.tvStatus.text =
                        "✅ Escaneado: ${pdf.pageCount} página(s) — ${savedFile.name}"
                    binding.btnSaveAndShare.isEnabled = true
                    binding.btnSave.isEnabled = true
                    binding.btnShare.isEnabled = true
                } else {
                    toast("No se pudo procesar el PDF escaneado.")
                }
            } else {
                toast("El escaneo no devolvió un PDF. Intenta de nuevo.")
            }
        }
    }

    // --- Lanzador que abre el selector de carpetas del sistema Android ---
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Guardamos permiso permanente sobre esta carpeta (sin esto se pierde al cerrar la app)
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            savedFolderUri = uri
            prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
            updateFolderLabel()

            if (pendingSaveAndShare) {
                pendingSaveAndShare = false
                saveAndShare()
            }
        } else {
            pendingSaveAndShare = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperar la carpeta guardada de una sesión anterior, si existe
        prefs.getString(KEY_FOLDER_URI, null)?.let { uriString ->
            savedFolderUri = Uri.parse(uriString)
        }
        updateFolderLabel()

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnChooseFolder.setOnClickListener { folderPickerLauncher.launch(null) }
        binding.btnSaveAndShare.setOnClickListener { saveAndShare() }
        binding.btnSave.setOnClickListener { saveToChosenFolder() }
        binding.btnShare.setOnClickListener { shareCurrentPdf() }
    }

    /** Punto de entrada del botón "Escanear documento". La primera vez, muestra antes
     *  el aviso de "Agregar página" (para que no se confunda con "Siguiente"); luego
     *  ya no vuelve a aparecer y va directo al escáner. */
    private fun startScan() {
        if (prefs.getBoolean(KEY_SEEN_ADD_PAGE_TIP, false)) {
            launchDocumentScanner()
        } else {
            // Se marca como visto ya al mostrarlo (no solo al tocar "Entendido"): así el
            // aviso realmente aparece una única vez, aunque el usuario lo cierre sin leerlo.
            prefs.edit().putBoolean(KEY_SEEN_ADD_PAGE_TIP, true).apply()
            AddPageTipDialogFragment { launchDocumentScanner() }
                .show(supportFragmentManager, "add_page_tip")
        }
    }

    /** Abre la pantalla de escaneo de Google ML Kit (detección de bordes, recorte y PDF). */
    private fun launchDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                toast("No se pudo abrir el escáner: ${e.message}")
            }
    }

    /** Copia el PDF temporal que entrega ML Kit a una carpeta privada de la app,
     *  para poder guardarlo y compartirlo de forma confiable. */
    private fun copyScannedPdfToAppStorage(sourceUri: Uri): File? {
        return try {
            val pdfDir = File(cacheDir, "pdfs").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
            val destFile = File(pdfDir, "Escaneo_$timestamp.pdf")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            null
        }
    }

    /** Guarda el PDF en la carpeta elegida. Devuelve true solo si lo logró guardar
     *  (por ejemplo, devuelve false si todavía no hay carpeta elegida — en ese caso
     *  abre el selector de carpeta y el usuario tiene que volver a tocar el botón). */
    private fun saveToChosenFolder(): Boolean {
        val pdfFile = currentPdfFile ?: run {
            toast("Primero escanea un documento.")
            return false
        }
        val folderUri = savedFolderUri
        if (folderUri == null) {
            toast("Primero elige una carpeta para guardar.")
            folderPickerLauncher.launch(null)
            return false
        }

        return try {
            val folderDoc = DocumentFile.fromTreeUri(this, folderUri)
            if (folderDoc == null || !folderDoc.canWrite()) {
                toast("No se puede escribir en esa carpeta. Elige otra.")
                return false
            }
            val newFile = folderDoc.createFile("application/pdf", pdfFile.nameWithoutExtension)
                ?: run {
                    toast("No se pudo crear el archivo en la carpeta.")
                    return false
                }
            contentResolver.openOutputStream(newFile.uri)?.use { output ->
                pdfFile.inputStream().use { input -> input.copyTo(output) }
            }
            toast("PDF guardado en la carpeta ✅")
            true
        } catch (e: Exception) {
            toast("Error al guardar: ${e.message}")
            false
        }
    }

    /** Botón "Guardar y compartir": guarda en la carpeta elegida y, si se guardó bien,
     *  abre el selector para compartir. Si todavía no hay carpeta elegida, primero pide
     *  elegirla y continúa sola apenas el usuario la elige (ver folderPickerLauncher). */
    private fun saveAndShare() {
        if (currentPdfFile == null) {
            toast("Primero escanea un documento.")
            return
        }
        if (savedFolderUri == null) {
            pendingSaveAndShare = true
            folderPickerLauncher.launch(null)
            return
        }
        if (saveToChosenFolder()) {
            shareCurrentPdf()
        }
    }

    private fun shareCurrentPdf() {
        val pdfFile = currentPdfFile ?: run {
            toast("Primero escanea un documento.")
            return
        }
        val contentUri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))
    }

    private fun updateFolderLabel() {
        val uri = savedFolderUri
        binding.tvFolder.text = if (uri == null) {
            "Ninguna carpeta elegida todavía"
        } else {
            val name = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment
            "📁 $name"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_SEEN_ADD_PAGE_TIP = "seen_add_page_tip"
    }
}
