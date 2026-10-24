package com.walkman.tv.source.js

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ScriptHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `request honors the script timeout option`() {
        server.enqueue(
            MockResponse()
                .setHeadersDelay(500, TimeUnit.MILLISECONDS)
                .setBody("ok"),
        )
        val completion = CountDownLatch(1)
        val error = AtomicReference<String?>()

        ScriptHttpClient(OkHttpClient()).send(
            requestKey = "slow-request",
            url = server.url("/slow").toString(),
            options = JSONObject().put("timeout", 50),
        ) { message, _ ->
            error.set(message)
            completion.countDown()
        }

        assertTrue("request did not finish", completion.await(2, TimeUnit.SECONDS))
        assertNotNull("request ignored options.timeout", error.get())
    }
}
