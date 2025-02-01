package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addbutton
import org.jetbrains.compose.resources.painterResource


@Composable
fun Addbutton() {
    val imgSize = 80
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 113.dp, end = 45.dp),
        Alignment.BottomEnd
    ) {
        Image(
            painter = painterResource(Res.drawable.addbutton),
            contentDescription = null,
            modifier = Modifier
                .size(imgSize.dp)
                .clickable { /*TODO*/ }
        )
    }
}