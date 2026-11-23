package com.example.features.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.DeepRoyalBlueDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SapphireBlue

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val presenter: SplashPresenter = viewModel(
        factory = viewModelFactory {
            initializer { SplashPresenter(context) }
        }
    )
    val state by presenter.uiState.collectAsStateWithLifecycle()
    val logoScale = remember { Animatable(0.75f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        presenter.startSessionCheck(onSplashComplete)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepRoyalBlueDark, DeepRoyalBlue, CobaltAccent)
                )
            )
            .testTag("splash_screen")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.85f, size.height * 0.14f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = SapphireBlue.copy(alpha = 0.28f),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.12f, size.height * 0.88f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale.value),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                shadowElevation = 16.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingBag,
                        contentDescription = "ShopSwift brand emblem",
                        tint = DeepRoyalBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ShopSwift",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Curated modern commerce, delivered swiftly.",
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite.copy(alpha = 0.82f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = PureWhite,
                trackColor = PureWhite.copy(alpha = 0.22f),
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = state.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = PureWhite.copy(alpha = 0.75f)
            )
        }
    }
}
