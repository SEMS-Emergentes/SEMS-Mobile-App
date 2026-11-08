package pe.edu.upc.sems.reports.domain

/** Writes the report as a PDF into the phone's Downloads and tells where (a content URI), or null if it failed. */
interface ReportFiles {
    suspend fun savePdf(data: ReportData, fileName: String): String?
}
