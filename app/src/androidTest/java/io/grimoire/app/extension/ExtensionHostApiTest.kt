package io.grimoire.app.extension

import android.content.pm.PackageManager
import android.webkit.CookieManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.source.Source
import io.grimoire.api.source.feature.WebViewLoginSource
import io.grimoire.api.source.http.HttpSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
        val fixtures = mapOf(
            "en.webnovel" to listOf("uid=397; Domain=.webnovel.com; Path=/; Secure"),
            "en.azurechronicles" to listOf("wordpress_logged_in_397=fixture; Path=/; Secure"),
            "en.zlibrary" to listOf("remix_userid=397; Path=/; Secure", "remix_userkey=fixture; Path=/; Secure"),
            "en.caleredhair" to listOf("session_id=fixture; Path=/; Secure"),
        )
        for ((name, cookies) in fixtures) {
            val source = load(name)
            val login = source as WebViewLoginSource
            val written = CountDownLatch(cookies.size)
            val accepted = AtomicBoolean(true)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                for (cookie in cookies) {
                    CookieManager.getInstance().setCookie((source as HttpSource).baseUrl, cookie) {
                        if (!it) accepted.set(false)
                        written.countDown()
                    }
                }
            }
            assertTrue("$name host cookie write timed out", written.await(5, TimeUnit.SECONDS))
            assertTrue("$name host cookies were rejected", accepted.get())
            assertTrue("$name must see the host's signed-in fixture cookies", login.isLoggedIn())
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
    fun declaredRequirementsAreCheckedBeforeCachedOrMissingCodeCanLoad() {
        val info = fixtureInfo("en.webnovel")
        val cached = loader.load(info)!!.source
        assertNotNull(cached)
        val future = loader.load(info.copy(minimumApiVersion = "1.6"))
        assertNotNull("A rejected cached source must remain manageable", future)
        assertTrue(!future!!.isCompatible)
        assertNull(future.source)
        assertNotSame("Rejected requirements must release the cached source", cached, loader.load(info)!!.source)
        val malformed = loader.load(info.copy(
            minimumApiVersion = "invalid", apkPath = "missing.apk", sourceClassName = "MustNotLoad",
        ))
        assertNotNull("Rejection must happen before creating a class loader", malformed)
        assertNull(malformed!!.source)
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
    private fun fixtureInfo(name: String): ExtensionInfo {
        val packageName = "io.grimoire.extension.$name"
        val pkg = context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        val app = requireNotNull(pkg.applicationInfo)
        val info = ExtensionInfo(
            packageName, context.packageManager.getApplicationLabel(app).toString(),
            pkg.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(pkg),
            requireNotNull(app.metaData.getString("grimoire.extension.class")), app.sourceDir,
            if (app.metaData.containsKey("grimoire.extension.minApi")) app.metaData.getString("grimoire.extension.minApi").orEmpty() else null,
        )
        assertEquals("The fixture APK must declare its API requirement", "1.5", info.minimumApiVersion)
        return info
    }

    private fun load(name: String): Source {
        val info = fixtureInfo(name)
        val packageName = info.packageName
        val loaded = loader.load(info)
        assertNotNull("Could not load fixture $packageName", loaded)
        assertTrue("Fixture $packageName must be compatible", loaded!!.isCompatible)
        return requireNotNull(loaded.source)
    }
}
