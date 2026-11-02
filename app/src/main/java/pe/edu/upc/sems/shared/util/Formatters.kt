package pe.edu.upc.sems.shared.util

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Number formats of the project: `4 820 kWh`, `238 kW`, `95,5 kW`.
 * Thousands separated by a non-breaking space (so "4 820 kWh" never wraps), decimal comma.
 */
object Formatters {

    private const val NBSP = ' '

    private fun decimalFormat(maxFractionDigits: Int): DecimalFormat {
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = NBSP
            decimalSeparator = ','
        }
        return DecimalFormat("#,##0", symbols).apply {
            maximumFractionDigits = maxFractionDigits
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }
    }

    /** `95 kW`, `95,5 kW`, `1 250 kW`. */
    fun kilowatts(value: Double): String = "${decimalFormat(2).format(value)}${NBSP}kW"

    /** `600 m²`. */
    fun squareMeters(value: Double): String = "${decimalFormat(1).format(value)}${NBSP}m²"

    /** A number as typed back in a form field: `95,5`, no grouping. */
    fun editable(value: Double): String = decimalFormat(2).apply { isGroupingUsed = false }.format(value)
}
