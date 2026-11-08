package pe.edu.upc.sems.reports.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.upc.sems.reports.domain.ReportData
import pe.edu.upc.sems.reports.domain.ReportFiles
import pe.edu.upc.sems.settings.data.withLanguage

/**
 * Saves the PDF in the public Downloads folder through MediaStore. From Android 10 (our minSdk is 29) that needs
 * no storage permission, and the file shows up in the Files app.
 */
@Singleton
class AndroidReportFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languages: pe.edu.upc.sems.settings.domain.LanguageStore,
) : ReportFiles {

    override suspend fun savePdf(data: ReportData, fileName: String): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            // The PDF is written in the language the app is in: the application context itself is not.
            val localized = context.withLanguage(languages.get())
            resolver.openOutputStream(uri)?.use { PdfReportWriter(localized).write(data, it) } ?: error("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri.toString()
        } catch (e: CancellationException) {
            resolver.delete(uri, null, null)
            throw e
        } catch (e: Exception) {
            // A half-written file must not stay in Downloads.
            resolver.delete(uri, null, null)
            null
        }
    }
}
