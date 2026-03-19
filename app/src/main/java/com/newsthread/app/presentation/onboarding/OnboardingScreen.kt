package com.newsthread.app.presentation.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsthread.app.presentation.theme.ProjectTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                onNext = {
                    if (pagerState.currentPage < 3) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish()
                    }
                },
                onSkip = onFinish,
                showSkip = pagerState.currentPage >= 1
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                OnboardingPage(page)
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val content = when (page) {
        0 -> OnboardingContent(
            title = "Welcome to NewsThread",
            description = "Experience the full spectrum of news. Break out of the echo chamber one story at a time.",
            icon = "🌐"
        )
        1 -> OnboardingContent(
            title = "See the Bias",
            description = "Our AI analyzes perspectives from Left, Center, and Right. Look for the pulse indicator on every story.",
            icon = "📊"
        )
        2 -> OnboardingContent(
            title = "Track What Matters",
            description = "Found a story you want to follow? Hit 'Track' to stay updated as new perspectives emerge. (Deep context is key!)",
            icon = "🎯"
        )
        else -> OnboardingContent(
            title = "Privacy First",
            description = "No tracking, no ads. Just pure information analyzed for your insight. Ready to start?",
            icon = "🛡️"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ProjectTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = content.icon,
            fontSize = 80.sp,
            modifier = Modifier.padding(bottom = ProjectTheme.spacing.l)
        )
        Text(
            text = content.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
        Text(
            text = content.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    showSkip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ProjectTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Linear progress bar (smooth integration)
        val progress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = (currentPage + 1) / 4f,
            label = "onboardingProgress"
        )
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (showSkip) {
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            style = ProjectTheme.typography.labelSmallProminent,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Button(
                onClick = onNext,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = ProjectTheme.spacing.l, vertical = ProjectTheme.spacing.s)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (currentPage == 3) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(ProjectTheme.spacing.xs))
                    Icon(
                        imageVector = if (currentPage == 3) Icons.Default.Done else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class OnboardingContent(
    val title: String,
    val description: String,
    val icon: String
)
