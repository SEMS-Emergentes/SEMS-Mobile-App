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

    /**
     * False (the default) writes numbers the Spanish way, as above. The app sets it to true while it is in
     * English: `4,820 kWh`, `95.5 kW`, `S/ 52.40`.
     */
    @Volatile
    var useEnglishFormat: Boolean = false

    private fun decimalFormat(maxFractionDigits: Int): DecimalFormat {
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = if (useEnglishFormat) ',' else NBSP
            decimalSeparator = if (useEnglishFormat) '.' else ','
        }
        return DecimalFormat("#,##0", symbols).apply {
            maximumFractionDigits = maxFractionDigits
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }
    }

    /** `95 kW`, `95,5 kW`, `1 250 kW`. */
    fun kilowatts(value: Double): String = "${decimalFormat(2).format(value)}${NBSP}kW"

    /** A number alone, for cards that show the unit apart: `118`, `6,1`, `4 820`. */
    fun quantity(value: Double, maxFractionDigits: Int = 1): String = decimalFormat(maxFractionDigits).format(value)

    /** `118 kWh`, `4 820 kWh`, `12,4 kWh`. */
    fun kilowattHours(value: Double): String = "${decimalFormat(1).format(value)}${NBSP}kWh"

    /** `S/ 52,40`, `S/ 3 412,50`: soles always with two decimals. */
    fun soles(value: Double): String =
        "S/$NBSP${decimalFormat(2).apply { minimumFractionDigits = 2 }.format(value)}"

    /** `600 m²`. */
    fun squareMeters(value: Double): String = "${decimalFormat(1).format(value)}${NBSP}m²"

    /** A number as typed back in a form field: `95,5`, no grouping. */
    fun editable(value: Double): String = decimalFormat(2).apply { isGroupingUsed = false }.format(value)
}
