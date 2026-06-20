package io.grimoire.app.ui.tour

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin bridge so composables can reach the singleton [TourController] via Hilt. */
@HiltViewModel
class TourViewModel @Inject constructor(
    val controller: TourController,
) : ViewModel()
