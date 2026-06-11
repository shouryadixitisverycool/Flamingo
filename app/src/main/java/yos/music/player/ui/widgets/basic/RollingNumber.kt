package yos.music.player.ui.widgets.basic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun RollingNumberText(
    targetValue: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold)
)
{
    val formattedNumber = String.format("%,d", targetValue)

    Row(modifier = modifier) {
        formattedNumber.forEachIndexed { characterIndex, character ->
            val slotKey = formattedNumber.length - characterIndex
            AnimatedContent(
                targetState = character,
                label = "RollingNumberDigit_$slotKey",
                transitionSpec = {
                    if (targetState.isDigit() && initialState.isDigit() && targetState > initialState)
                    {
                        (slideInVertically(animationSpec = tween(300)) { fullHeight -> fullHeight } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically(animationSpec = tween(300)) { fullHeight -> -fullHeight } + fadeOut(tween(220)))
                    }
                    else
                    {
                        (slideInVertically(animationSpec = tween(300)) { fullHeight -> -fullHeight } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically(animationSpec = tween(300)) { fullHeight -> fullHeight } + fadeOut(tween(220)))
                    }
                }
            ) { displayCharacter ->
                Text(text = displayCharacter.toString(), style = style)
            }
        }
    }
}
