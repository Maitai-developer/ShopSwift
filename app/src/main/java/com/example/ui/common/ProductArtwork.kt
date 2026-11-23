package com.example.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R

enum class ProductArtworkPreset(val key: String, val label: String) {
    HEADPHONES("headphones", "Wireless Headphones"),
    WATCH("watch", "Smart Chronograph"),
    BACKPACK("backpack", "Commuter Backpack"),
    ACCESSORIES("accessories", "Tech Accessories"),
    HOMETECH("hometech", "Smart Home Hub")
}

@Composable
fun ProductArtwork(
    imageKey: String,
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("https://")) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = {
                FallbackProductArtwork(imageKey = imageKey, contentDescription = contentDescription)
            },
            error = {
                FallbackProductArtwork(imageKey = imageKey, contentDescription = contentDescription)
            }
        )
    } else {
        FallbackProductArtwork(
            imageKey = imageKey,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

@Composable
private fun FallbackProductArtwork(
    imageKey: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val drawableRes = when (imageKey.lowercase()) {
        "headphones" -> R.drawable.img_product_headphones
        "watch" -> R.drawable.img_product_watch
        "backpack" -> R.drawable.img_product_backpack
        else -> null
    }

    if (drawableRes != null) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        val icon = when (imageKey.lowercase()) {
            "accessories" -> Icons.Rounded.DevicesOther
            "hometech" -> Icons.Rounded.Lightbulb
            "watch" -> Icons.Rounded.Watch
            "backpack" -> Icons.Rounded.Backpack
            else -> Icons.Rounded.Headphones
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
