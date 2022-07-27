package com.jessenw.assistedinjectviewmodel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.jessenw.assistedinjectviewmodel.ui.theme.AssistedInjectViewModelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistedInjectViewModelTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    CameraIndex(CameraIndexViewModel())
                }
            }
        }
    }
}

@Composable
fun CameraIndex(
    viewModel: CameraIndexViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(viewModel.cameras) {
            CameraIndexListItemView(it)
        }
    }
}

@Composable
fun CameraIndexListItemView(camera: Camera) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Weighting the text column allows the icon to have layout priority
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(4.dp)
        ) {
            CameraPreviewListItemText(
                camera.name,
                MaterialTheme.typography.subtitle2
            )
            Spacer(modifier = Modifier.height(4.dp))
            CameraPreviewListItemText(
                camera.manufacturer,
                MaterialTheme.typography.body2
            )
        }
    }
}

@Composable
fun CameraPreviewListItemText(
    text: String,
    style: TextStyle
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

class CameraIndexViewModel(
    val cameras: List<Camera> = listOf(
        Camera("OM-1", "Olympus"),
        Camera("35S", "Rollei"),
        Camera("X-T30", "Fujifilm"),
        Camera("MX", "Pentax"),
        Camera("A6400", "Sony"),
        Camera("Mju", "Olympus"),
        Camera("RZ-67", "Mamiya"),
        Camera("M10 Monochrome", "Leica")
    )
): ViewModel()

data class Camera(
    val name: String,
    val manufacturer: String
)
