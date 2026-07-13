package sample.app

import androidx.compose.runtime.Composable
import sample.app.player.rememberPlayerController
import sample.app.ui.PlayerScreen

/**
 * App entry point: builds the [sample.app.player.PlayerController] state holder
 * and feeds its state + actions into the stateless [PlayerScreen].
 */
@Composable
fun App() {
    val controller = rememberPlayerController()
    PlayerScreen(state = controller.state, actions = controller)
}
