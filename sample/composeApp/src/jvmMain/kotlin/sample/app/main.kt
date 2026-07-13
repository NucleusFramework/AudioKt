import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material2.MaterialDecoratedWindow
import dev.nucleusframework.window.material2.MaterialTitleBar
import sample.app.App

// nucleusApplication drives GraalVM initialization (java.library.path, locale,
// HiDPI) automatically. MaterialDecoratedWindow derives the window chrome from
// the surrounding MaterialTheme.colors — here the default (light) palette.
fun main() =
    nucleusApplication(backend = NucleusBackend.Tao) {
        MaterialTheme {
            MaterialDecoratedWindow(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
                title = "sample",
                minimumSize = DpSize(350.dp, 600.dp),
            ) {
                MaterialTitleBar {
                    Text(title, modifier = Modifier.padding(horizontal = 12.dp))
                }
                App()
            }
        }
    }
