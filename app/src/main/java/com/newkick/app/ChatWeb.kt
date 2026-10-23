package com.newkick.app

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * O chat (WebView em kick.com) também serve para ações que a Kick protege com anti-bot, como
 * seguir/deixar de seguir: o `fetch` roda dentro da página, que já tem o desafio resolvido.
 */
object ChatWeb {
    @Volatile
    var webView: WebView? = null

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    class Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int) {
            pending.remove(id)?.complete(status)
        }
    }

    const val STATUS_NO_PAGE = -2
    const val STATUS_NO_SESSION = -3
    const val STATUS_TIMEOUT = -4

    /** Segue ou deixa de seguir um canal na página de chat aberta. Devolve o status HTTP (2xx = ok) ou um código negativo. */
    suspend fun setFollow(slug: String, follow: Boolean, page: WebView? = webView): Int {
        val wv = page ?: return STATUS_NO_PAGE
        val token = KickSession.token() ?: return STATUS_NO_SESSION
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<Int>()
        pending[id] = result
        val method = if (follow) "POST" else "DELETE"
        val js = "(function(){var x=(document.cookie.match(/XSRF-TOKEN=([^;]+)/)||[])[1];" +
            "var h={'Accept':'application/json','x-app-platform':'web','Authorization':'Bearer '+${JSONObject.quote(token)}};" +
            "if(x){h['x-xsrf-token']=decodeURIComponent(x)}" +
            "fetch('/api/v2/channels/'+${JSONObject.quote(slug)}+'/follow',{method:'$method',credentials:'include',headers:h})" +
            ".then(function(r){NewKick.onResult('$id',r.status)}).catch(function(){NewKick.onResult('$id',-1)})})();"
        withContext(Dispatchers.Main) { wv.evaluateJavascript(js, null) }
        return withTimeoutOrNull(15_000) { result.await() } ?: STATUS_TIMEOUT.also { pending.remove(id) }
    }

    /** Segue/deixa de seguir de qualquer tela: usa o chat aberto ou cria uma página escondida só para isso. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun setFollowAnywhere(context: Context, slug: String, follow: Boolean): Int {
        webView?.let { return setFollow(slug, follow, it) }
        val loaded = CompletableDeferred<Boolean>()
        val hidden = withContext(Dispatchers.Main) {
            WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                addJavascriptInterface(Bridge(), "NewKick")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.complete(true)
                    }
                }
                loadUrl("https://kick.com/popout/$slug/chat")
            }
        }
        return try {
            if (withTimeoutOrNull(20_000) { loaded.await() } != true) return STATUS_NO_PAGE
            delay(2_500) // dá tempo da proteção anti-bot da página carregar
            setFollow(slug, follow, hidden)
        } finally {
            withContext(Dispatchers.Main) { hidden.destroy() }
        }
    }
}
