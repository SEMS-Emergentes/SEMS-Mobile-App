package pe.edu.upc.sems.reports.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.R
import pe.edu.upc.sems.reports.domain.ReportData
import pe.edu.upc.sems.reports.presentation.formatDuration
import pe.edu.upc.sems.reports.presentation.reportPeriodLabel
import pe.edu.upc.sems.shared.util.Formatters

/**
 * Draws the report with Android's own PdfDocument (no library). Page 1 is what the preview shows: figures, sites,
 * alerts and saving. Page 2 compares the demand with the contracted power of each site.
 */
class PdfReportWriter(private val context: Context) {

    private val pageWidth = 595 // A4 in points
    private val pageHeight = 842
    private val margin = 40f

    private val blue = Color.rgb(11, 87, 208)
    private val ink = Color.rgb(26, 28, 30)
    private val muted = Color.rgb(67, 71, 78)
    private val line = Color.rgb(195, 199, 207)
    private val green = Color.rgb(20, 108, 46)
    private val boxFill = Color.rgb(240, 244, 250)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun write(data: ReportData, out: OutputStream) {
        val document = PdfDocument()
        val pages = if (data.sites.isNotEmpty()) 2 else 1
        drawPage(document, 1) { canvas -> firstPage(canvas, data, pages) }
        if (pages == 2) drawPage(document, 2) { canvas -> secondPage(canvas, data, pages) }
        document.writeTo(out)
        document.close()
    }

    private fun drawPage(document: PdfDocument, number: Int, draw: (Canvas) -> Unit) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create())
        draw(page.canvas)
        document.finishPage(page)
    }

    private fun header(canvas: Canvas, data: ReportData, title: String, page: Int, pages: Int): Float {
        var y = margin + 16
        canvas.drawText(title, margin, y, paint(18f, bold = true))
        val pageText = context.getString(R.string.report_page, page, pages)
        val pagePaint = paint(10f, color = muted)
        canvas.drawText(pageText, pageWidth - margin - pagePaint.measureText(pageText), y, pagePaint)
        y += 22
        val period = reportPeriodLabel(data.range)
        canvas.drawText(context.getString(R.string.report_subtitle, period, data.organizationName), margin, y, paint(11f, color = muted))
        y += 10
        canvas.drawLine(margin, y, pageWidth - margin, y, paint(1f).apply { color = line })
        return y + 22
    }

    private fun footer(canvas: Canvas, data: ReportData) {
        val text = context.getString(R.string.report_footer, data.generatedOn.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        canvas.drawText(text, margin, pageHeight - margin, paint(9f, color = muted))
    }

    private fun firstPage(canvas: Canvas, data: ReportData, pages: Int) {
        var y = header(canvas, data, context.getString(R.string.report_pdf_title), 1, pages)

        // Figures: consumption, cost, demand.
        val columnWidth = (pageWidth - 2 * margin) / 3
        val figures = listOf(
            context.getString(R.string.report_kpi_consumption) to Formatters.kilowattHours(data.totalKwh),
            context.getString(R.string.report_kpi_cost) to (data.totalCostSoles?.let(Formatters::soles) ?: "—"),
            context.getString(R.string.report_kpi_demand) to (data.peakKw?.let(Formatters::kilowatts) ?: "—"),
        )
        figures.forEachIndexed { i, (label, value) ->
            val x = margin + i * columnWidth
            canvas.drawText(label, x, y, paint(10f, color = muted))
            canvas.drawText(value, x, y + 20, paint(15f, bold = true))
        }
        y += 44
        canvas.drawLine(margin, y, pageWidth - margin, y, paint(1f).apply { color = line })
        y += 26

        // Sites: kWh and soles.
        val kwhRight = margin + (pageWidth - 2 * margin) * 0.68f
        val solesRight = pageWidth - margin
        val head = paint(10f, color = muted)
        canvas.drawText(context.getString(R.string.report_col_site), margin, y, head)
        canvas.drawText(context.getString(R.string.report_col_kwh), kwhRight - head.measureText(context.getString(R.string.report_col_kwh)), y, head)
        canvas.drawText(context.getString(R.string.report_col_soles), solesRight - head.measureText(context.getString(R.string.report_col_soles)), y, head)
        y += 8
        canvas.drawLine(margin, y, pageWidth - margin, y, paint(1f).apply { color = line })
        y += 20

        val body = paint(12f)
        val bold = paint(12f, bold = true)
        data.sites.forEach { site ->
            canvas.drawText(site.siteName, margin, y, body)
            val kwh = Formatters.quantity(site.kwh, 0)
            canvas.drawText(kwh, kwhRight - body.measureText(kwh), y, body)
            val soles = site.costSoles?.let(Formatters::soles) ?: "—"
            canvas.drawText(soles, solesRight - body.measureText(soles), y, body)
            y += 26
        }
        canvas.drawLine(margin, y - 16, pageWidth - margin, y - 16, paint(1f).apply { color = line })
        canvas.drawText(context.getString(R.string.report_total), margin, y + 2, bold)
        val totalKwh = Formatters.quantity(data.totalKwh, 0)
        canvas.drawText(totalKwh, kwhRight - bold.measureText(totalKwh), y + 2, bold)
        val totalSoles = data.totalCostSoles?.let(Formatters::soles) ?: "—"
        canvas.drawText(totalSoles, solesRight - bold.measureText(totalSoles), y + 2, bold)
        y += 18
        canvas.drawText(context.getString(R.string.report_note_cost), margin, y + 8, paint(9f, color = muted))
        y += 34

        // Alerts and saving.
        val boxTop = y
        val lines = mutableListOf<Pair<String, Int>>()
        val response = data.avgResponseMinutes?.let { context.getString(R.string.report_alerts_response, formatDuration(it)) }
        val attended = context.resources.getQuantityString(R.plurals.report_alerts_attended, data.alertsAttended, data.alertsAttended)
        lines += (if (response != null) "$attended · $response" else attended) to ink
        data.appliedSavingSoles?.let { lines += context.getString(R.string.report_saving, Formatters.soles(it)) to green }
        val boxHeight = 20f + lines.size * 20f
        canvas.drawRoundRect(margin, boxTop, pageWidth - margin, boxTop + boxHeight, 10f, 10f, paint(1f).apply { color = boxFill })
        lines.forEachIndexed { i, (text, color) ->
            canvas.drawText(text, margin + 14, boxTop + 24 + i * 20f, paint(11f, color = color))
        }

        if (!data.hasReadings) {
            canvas.drawText(context.getString(R.string.report_no_readings), margin, boxTop + boxHeight + 24, paint(11f, color = muted))
        }
        footer(canvas, data)
    }

    private fun secondPage(canvas: Canvas, data: ReportData, pages: Int) {
        var y = header(canvas, data, context.getString(R.string.report_demand_title), 2, pages)
        val peakRight = margin + (pageWidth - 2 * margin) * 0.62f
        val contractedRight = margin + (pageWidth - 2 * margin) * 0.82f
        val useRight = pageWidth - margin
        val head = paint(10f, color = muted)
        fun right(text: String, x: Float, yy: Float, p: Paint) = canvas.drawText(text, x - p.measureText(text), yy, p)
        canvas.drawText(context.getString(R.string.report_col_site), margin, y, head)
        right(context.getString(R.string.report_col_peak), peakRight, y, head)
        right(context.getString(R.string.report_col_contracted), contractedRight, y, head)
        right(context.getString(R.string.report_col_use), useRight, y, head)
        y += 8
        canvas.drawLine(margin, y, pageWidth - margin, y, paint(1f).apply { color = line })
        y += 20
        val body = paint(12f)
        data.sites.forEach { site ->
            canvas.drawText(site.siteName, margin, y, body)
            right(site.peakKw?.let(Formatters::kilowatts) ?: "—", peakRight, y, body)
            right(Formatters.kilowatts(site.contractedKw), contractedRight, y, body)
            val use = site.peakKw?.takeIf { site.contractedKw > 0 }?.let { "${Formatters.quantity(it / site.contractedKw * 100, 0)} %" } ?: "—"
            right(use, useRight, y, body)
            y += 26
        }
        footer(canvas, data)
    }
}
