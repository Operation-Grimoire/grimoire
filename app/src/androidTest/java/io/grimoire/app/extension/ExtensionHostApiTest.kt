package io.grimoire.app.extension

import android.content.pm.PackageManager
import android.webkit.CookieManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.source.Source
import io.grimoire.api.source.feature.WebViewLoginSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Runs against installed extension APKs, without linking their implementation into the app. */
@RunWith(AndroidJUnit4::class)
class ExtensionHostApiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val loader = ExtensionLoader(context)

    @Before
    fun requireFixtures() {
        assumeTrue(
            "Install the API fixture APKs and pass -e extensionApiFixtures true",
            InstrumentationRegistry.getArguments().getString("extensionApiFixtures") == "true",
        )
    }

    @Test
    fun installedLoginSourcesCanLogOut() = runBlocking {
        for (name in listOf("en.webnovel", "en.azurechronicles", "en.zlibrary", "en.caleredhair")) {
            val source = load(name)
            val login = source as WebViewLoginSource
            if (name == "en.webnovel") {
                val written = CountDownLatch(1)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    CookieManager.getInstance().setCookie(
                        "https://www.webnovel.com", "uid=397; Domain=.webnovel.com; Path=/; Secure",
                    ) { written.countDown() }
                }
                assertTrue("Host cookie write timed out", written.await(5, TimeUnit.SECONDS))
                assertTrue("The extension must see the host's login cookie", login.isLoggedIn())
            }
            login.logout()
            assertEquals("$name must be signed out after logout", false, login.isLoggedIn())
        }
    }

    @Test
    fun installedSourceUsesTheHostCookieApi() {
        val source = load("en.webnovel")
        val cookies = source.javaClass.classLoader!!.loadClass("io.grimoire.api.network.SourceCookies")
        assertSame("The source must use the host cookie API", Source::class.java.classLoader, cookies.classLoader)
    }

    @Test
    fun installedGalaxySourceCanReachTheHostPageRenderer() = runBlocking {
        val source = load("ar.galaxynovels")
        val browser = source.javaClass.classLoader!!.loadClass("io.grimoire.api.network.SourceBrowser")
        val instance = browser.getField("INSTANCE").get(null)
        assertTrue("The initialized host must provide rendered-page browsing", browser.getMethod("getReady").invoke(instance) as Boolean)
        assertSame("The renderer API must come from the host", Source::class.java.classLoader, browser.classLoader)

        // Reflection also lets this regression compile against the old host for fail-before proof.
        val reading = suspendCoroutineUninterceptedOrReturn<Any> { continuation ->
            val accept = requireNotNull(browser.classLoader!!.loadClass("io.grimoire.api.network.SourceDocumentDecision\$Accept")
                .getField("INSTANCE").get(null))
            val decide: (Any) -> Any = { accept }
            browser.getMethod(
                "read", String::class.java, String::class.java, Long::class.javaPrimitiveType,
                kotlin.jvm.functions.Function1::class.java, Continuation::class.java,
            ).invoke(
                instance, "data:text/html,<title>grimoire-api-fixture</title>",
                "document.title", 10_000L, decide, continuation,
            )
        }
        assertEquals("\"grimoire-api-fixture\"", reading.javaClass.getMethod("getJavascript").invoke(reading))
    }

    @Suppress("DEPRECATION")
    private fun load(name: String): Source {
        val packageName = "io.grimoire.extension.$name"
        val pkg = context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        val app = requireNotNull(pkg.applicationInfo)
        val info = ExtensionInfo(
            packageName, context.packageManager.getApplicationLabel(app).toString(),
            pkg.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(pkg),
            requireNotNull(app.metaData.getString("grimoire.extension.class")), app.sourceDir,
        )
        val source = loader.load(info)
        assertNotNull("Could not load fixture $packageName", source)
        return requireNotNull(source)
    }
}
