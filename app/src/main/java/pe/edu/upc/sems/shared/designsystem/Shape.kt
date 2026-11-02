package pe.edu.upc.sems.shared.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SEMS radii: 4 dp text fields · 12–16 dp cards · 16 dp FAB.
 * Buttons use the M3 default pill shape (CircleShape), so they are not set here.
 */
val SemsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // text fields
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // cards
    large = RoundedCornerShape(16.dp), // large cards, FAB
    extraLarge = RoundedCornerShape(28.dp), // dialogs, bottom sheets
)
