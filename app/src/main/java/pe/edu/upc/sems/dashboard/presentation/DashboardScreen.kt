package pe.edu.upc.sems.dashboard.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.edu.upc.sems.organizations.presentation.gettingstarted.GettingStartedCard
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.ComingSoonScreen

/**
 * "Resumen". For now: "Primeros pasos" while the account has no company or site (as in the web app's
 * dashboard), and a placeholder until the dashboard context is built in part 2.
 */
@Composable
fun DashboardScreen(
    onRegisterCompany: () -> Unit,
    onAddFirstSite: (organizationId: String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        GettingStartedCard(
            onRegisterCompany = onRegisterCompany,
            onAddFirstSite = onAddFirstSite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SemsSpacing.md, vertical = SemsSpacing.sm),
        )
        Box(Modifier.weight(1f)) {
            ComingSoonScreen()
        }
    }
}
