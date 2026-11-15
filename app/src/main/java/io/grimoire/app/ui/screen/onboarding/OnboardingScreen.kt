package io.grimoire.app.ui.screen.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.ArrowBack
import io.grimoire.app.ui.icon.ArrowForward
import kotlinx.coroutines.launch

private const val ONBOARDING_STEP_COUNT = 6

/**
 * First-run guided setup flow using a swipeable [HorizontalPager]:
 * 0. Welcome, App UI language, and returning-user backup restore
 * 1. Reading (content) languages with search & quick clear
 * 2. Theme & appearance with live preview
 * 3. Contextual notification permission
 * 4. Privacy & diagnostics toggles
 * 5. Quick-start actions (extensions / book import) and optional spotlight tour
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOpenExtensions: () -> Unit = {},
    onImportBook: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_STEP_COUNT })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastStep = currentPage == ONBOARDING_STEP_COUNT - 1

    BackHandler(enabled = currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            OnboardingTopBar(
                currentPage = currentPage,
                pageCount = ONBOARDING_STEP_COUNT,
                showSkip = !isLastStep,
                onSkip = viewModel::skip,
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                canGoBack = currentPage > 0,
                isLastStep = isLastStep,
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                },
                onNext = {
                    if (isLastStep) {
                        viewModel.finish(startTour = false)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            when (page) {
                0 -> OnboardingWelcomeStep(viewModel = viewModel)
                1 -> OnboardingLanguagesStep(viewModel = viewModel)
                2 -> OnboardingAppearanceStep(viewModel = viewModel)
                3 -> OnboardingNotificationsStep()
                4 -> OnboardingPrivacyStep(viewModel = viewModel)
                else -> OnboardingReadyStep(
                    viewModel = viewModel,
                    onOpenExtensions = onOpenExtensions,
                    onImportBook = onImportBook,
                )
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentPage: Int,
    pageCount: Int,
    showSkip: Boolean,
    onSkip: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = (currentPage + 1) / pageCount.toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "onboardingProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_progress, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = showSkip,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    canGoBack: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                if (canGoBack) {
                    OutlinedButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_back))
                    }
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (isLastStep) R.string.onboarding_enter_library
                            else R.string.onboarding_continue,
                        ),
                    )
                    if (!isLastStep) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = AppIcons.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
