package io.github.darkryh.katalyst.tui.ui.screens

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Text

/**
 * Canonical empty state for a subsystem whose snapshot section is null: the backend simply is not
 * reporting it. States the fact, then the most likely reason ([hint], subsystem-specific), so the
 * user knows whether this is expected (module not used) or a problem (feature off).
 */
@Composable
fun SectionMissing(title: String, hint: String, theme: DispatchTheme) {
    Panel(title = title, titleStyle = theme.primary, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(1)) {
            Text("The attached backend is not reporting this section.", style = theme.secondary)
            Text(hint, style = theme.muted)
            Text("Esc to go back", style = theme.muted)
        }
    }
}
