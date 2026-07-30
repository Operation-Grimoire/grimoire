package io.grimoire.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/*
 * Material 3 motion patterns for the NavHost, hand-rolled instead of pulling in
 * material-motion-compose: shared axis X for hierarchical push/pop (what Mihon
 * uses), fade-through for lateral switches between the bottom-nav tabs.
 *
 * Both patterns share the same fade choreography: the outgoing screen fades out
 * over the first 90ms, then the incoming screen fades in over the remaining
 * 210ms, so the two are never fully visible at once.
 */

private const val NAV_MS = 300
private const val OUTGOING_MS = NAV_MS * 90 / 300
private const val INCOMING_MS = NAV_MS - OUTGOING_MS

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

/** True when both ends of the transition are bottom-nav tabs — lateral peers, not hierarchy. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in topLevelRoutes &&
        targetState.destination.route in topLevelRoutes

/** Incoming screen of a shared axis X step; slides in from the right on push, from the left on pop. */
internal fun sharedAxisXIn(forward: Boolean, slideDistance: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NAV_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { if (forward) slideDistance else -slideDistance },
    ) + fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing))

/** Outgoing screen of a shared axis X step; slides out in the same direction the incoming one enters. */
internal fun sharedAxisXOut(forward: Boolean, slideDistance: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NAV_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { if (forward) -slideDistance else slideDistance },
    ) + fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))

/** Incoming tab of a fade-through switch: fades in while settling from 92% scale. */
internal fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing), initialScale = 0.92f)

/** Outgoing tab of a fade-through switch: a plain quick fade, no scale. */
internal fun fadeThroughOut(): ExitTransition =
    fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))
