package pe.edu.upc.sems.settings.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.settings.domain.AppLanguage
import pe.edu.upc.sems.settings.domain.ThemeMode
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.session.SessionUser

/** "Configuración": profile, notifications and preferences (Figma M25). */
@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SemsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
    ) {
        state.user?.let { ProfileCard(it, onEditProfile) }

        Text(
            stringResource(R.string.settings_notifications),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SemsSpacing.xs),
        )
        OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            val enabled = state.emailEnabled
            Row(
                modifier = Modifier.fillMaxWidth().padding(SemsSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_email), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (state.emailFailed) R.string.settings_email_failed else R.string.settings_email_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.emailFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(SemsSpacing.sm))
                Switch(
                    checked = enabled == true,
                    onCheckedChange = viewModel::onEmailToggle,
                    enabled = enabled != null && !state.emailSaving,
                )
            }
        }

        Text(
            stringResource(R.string.settings_preferences),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SemsSpacing.xs),
        )
        OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            PreferenceRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(state.language.label()),
                onClick = { languageDialog = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.md))
            PreferenceRow(
                title = stringResource(R.string.settings_theme),
                value = stringResource(state.themeMode.label()),
                onClick = { themeDialog = true },
            )
        }

        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(top = SemsSpacing.xs),
        ) {
            Icon(painterResource(R.drawable.ic_logout), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(SemsSpacing.xs))
            Text(stringResource(R.string.settings_logout))
        }
    }

    if (languageDialog) {
        LanguageDialog(
            selected = state.language,
            onSelect = { language ->
                languageDialog = false
                if (language != state.language) {
                    viewModel.onLanguageChange(language)
                    // Every text of the app is read again, now in the chosen language.
                    context.findActivity()?.recreate()
                }
            },
            onDismiss = { languageDialog = false },
        )
    }

    if (themeDialog) {
        ThemeDialog(
            selected = state.themeMode,
            onSelect = {
                viewModel.onThemeChange(it)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }
}

@Composable
private fun ProfileCard(user: SessionUser, onEdit: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SemsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    initials(user),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(SemsSpacing.md),
                )
            }
            Spacer(Modifier.width(SemsSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(user.fullName?.takeIf { it.isNotBlank() } ?: user.emailAddress, style = MaterialTheme.typography.titleMedium)
                Text(user.emailAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                roleLabel(user)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.settings_edit_profile))
            }
        }
    }
}

@Composable
private fun PreferenceRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(SemsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                            .padding(vertical = SemsSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Spacer(Modifier.width(SemsSpacing.sm))
                        Text(stringResource(mode.label()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}

@Composable
private fun LanguageDialog(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == selected, role = Role.RadioButton, onClick = { onSelect(language) })
                            .padding(vertical = SemsSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == selected, onClick = null)
                        Spacer(Modifier.width(SemsSpacing.sm))
                        Text(stringResource(language.label()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}

/** Each language is always named in its own language, so the user can find theirs whatever the app shows. */
private fun AppLanguage.label(): Int = when (this) {
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.SPANISH -> R.string.settings_language_spanish
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** Two letters for the avatar: first letters of the first two words of the name, or of the email. */
internal fun initials(user: SessionUser): String {
    val words = user.fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = if (words.isNotEmpty()) {
        words.take(2).map { it.first() }
    } else {
        listOf(user.emailAddress.firstOrNull() ?: '?')
    }
    return letters.joinToString("").uppercase()
}

/** The account role the web shows (IAM role): ADMIN or STAFF. Null for a role the app does not name. */
@Composable
private fun roleLabel(user: SessionUser): String? = when {
    "ADMIN" in user.roles -> stringResource(R.string.settings_role_admin)
    "STAFF" in user.roles -> stringResource(R.string.settings_role_staff)
    else -> user.roles.firstOrNull()
}
