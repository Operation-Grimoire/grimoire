package io.grimoire.app.ui.tour

import io.grimoire.app.ui.ROUTE_NOVEL_DETAIL
import io.grimoire.app.ui.ROUTE_READER
import io.grimoire.app.ui.ROUTE_WEBVIEW
import io.grimoire.app.ui.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourRulesTest {

    @Test
    fun `tour ids are unique`() {
        assertEquals(grimoireTours.size, grimoireTours.map { it.id }.toSet().size)
    }

    @Test
    fun `every tour has steps`() {
        grimoireTours.forEach { assertTrue(it.id.name, it.steps.isNotEmpty()) }
    }

    @Test
    fun `navigating tours give every step a route`() {
        // The engine navigates per step, so a tour that can start anywhere
        // must say where each step lives.
        grimoireTours.filter { it.screenRoute == null }.forEach { tour ->
            tour.steps.forEachIndexed { i, step -> assertTrue("${tour.id} step $i", step.route != null) }
        }
    }

    @Test
    fun `screen-bound tours never navigate`() {
        grimoireTours.filter { it.screenRoute != null }.forEach { tour ->
            tour.steps.forEachIndexed { i, step ->
                assertNull("${tour.id} step $i", step.route)
                assertFalse("${tour.id} step $i", step.advanceOnReach)
            }
        }
    }

    @Test
    fun `each screen has at most one first-visit tour`() {
        val routes = grimoireTours.mapNotNull { it.screenRoute }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `first-visit tour is found by its screen route`() {
        assertEquals(TourId.Reader, grimoireTours.firstVisitTourFor(ROUTE_READER)?.id)
        assertEquals(TourId.NovelDetail, grimoireTours.firstVisitTourFor(ROUTE_NOVEL_DETAIL)?.id)
        assertNull(grimoireTours.firstVisitTourFor(TopLevelDestination.Library.route))
        assertNull(grimoireTours.firstVisitTourFor(null))
    }

    @Test
    fun `screen-bound tour ends only when the user leaves its screen`() {
        val reader = tourById(TourId.Reader)
        assertFalse(reader.leftItsScreen(ROUTE_READER))
        assertTrue(reader.leftItsScreen(ROUTE_WEBVIEW))
        assertFalse(reader.leftItsScreen(null))
    }

    @Test
    fun `navigating tours never end on a route change`() {
        val library = tourById(TourId.Library)
        assertFalse(library.leftItsScreen(ROUTE_READER))
    }

    @Test
    fun `reveal watch fires when the target appears`() {
        val watch = RevealWatch(TourKey.ReaderSettings)
        assertFalse(watch.update(emptySet()))
        assertTrue(watch.update(setOf(TourKey.ReaderSettings)))
    }

    @Test
    fun `reveal watch ignores a target that is already showing`() {
        // Stepping Back onto the step with the controls up shouldn't bounce forward.
        val watch = RevealWatch(TourKey.ReaderSettings)
        assertFalse(watch.update(setOf(TourKey.ReaderSettings)))
        assertFalse(watch.update(setOf(TourKey.ReaderSettings, TourKey.ReaderMarkRead)))
        assertFalse(watch.update(emptySet()))
        assertTrue(watch.update(setOf(TourKey.ReaderSettings)))
    }
}
