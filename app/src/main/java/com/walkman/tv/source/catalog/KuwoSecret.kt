package com.walkman.tv.source.catalog

import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Computes Kuwo's web anti-crawler `Secret` header.
 *
 * Kuwo's frontend derives it from a cookie value via a stream cipher whose "fold" step relies on
 * JavaScript Number→string (exponential-notation) + parseInt truncation semantics that do NOT
 * port cleanly to Kotlin integer math. Rather than risk a subtly-wrong reimplementation, we run
 * the original JS verbatim in the already-bundled QuickJS engine — guaranteed byte-identical to
 * the browser. The context is pinned to one thread since QuickJS is not thread-safe.
 *
 * Usage: server only checks that `Secret` decrypts to the `Hm_Iuvt_...` cookie value we send, so
 * a fabricated (cookie, Secret) pair is accepted — no real Baidu-analytics cookie needed.
 */
object KuwoSecret {
    /** Cookie name the algorithm keys on (embedded in Kuwo's own JS). */
    const val COOKIE_NAME = "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324"

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "kw-secret") }
    private val dispatcher = executor.asCoroutineDispatcher()
    @Volatile private var ctx: QuickJSContext? = null

    // Verbatim port of Kuwo's `Secret` generator (function f in their bundle). Do not "clean up"
    // the arithmetic — the exponential-notation fold is load-bearing.
    private const val JS = """
        function __kwSecret(t, e){
          if (e == null || e.length <= 0) return "";
          var n = "";
          for (var i = 0; i < e.length; i++) n += e.charCodeAt(i).toString();
          var o = Math.floor(n.length / 5);
          var r = parseInt(n.charAt(o) + n.charAt(2*o) + n.charAt(3*o) + n.charAt(4*o) + n.charAt(5*o));
          var c = Math.ceil(e.length / 2);
          var l = Math.pow(2, 31) - 1;
          if (r < 2) return "";
          var d = Math.round(1e9 * Math.random()) % 1e8;
          for (n += d; n.length > 10;) n = (parseInt(n.substring(0,10)) + parseInt(n.substring(10, n.length))).toString();
          n = (r * n + c) % l;
          var f = "", h = "";
          for (i = 0; i < t.length; i++) {
            h += (f = parseInt(t.charCodeAt(i) ^ Math.floor(n / l * 255))) < 16 ? "0" + f.toString(16) : f.toString(16);
            n = (r * n + c) % l;
          }
          for (d = d.toString(16); d.length < 8;) d = "0" + d;
          return h + d;
        }
    """

    /** Compute the Secret for a given `Hm_Iuvt_...` cookie value. Returns "" on any failure. */
    suspend fun compute(cookieValue: String): String = withContext(dispatcher) {
        val c = ctx ?: runCatching {
            QuickJSContext.create().also { it.evaluate(JS) }
        }.getOrNull()?.also { ctx = it } ?: return@withContext ""
        runCatching {
            c.globalObject.getJSFunction("__kwSecret").call(cookieValue, COOKIE_NAME) as? String
        }.getOrNull().orEmpty()
    }
}
