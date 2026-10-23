package com.newkick.app

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.net.URLDecoder

/**
 * Sessão da Kick. O login acontece na própria página da Kick (WebView) e o cookie fica no
 * armazenamento privado do app; aqui só lemos esse cookie para chamar a API como o site faz.
 * O app nunca vê nem guarda usuário/senha.
 */
object KickSession {
    private const val SITE = "https://kick.com"

    fun cookieHeader(): String? = CookieManager.getInstance().getCookie(SITE)?.takeIf { it.isNotBlank() }

    private fun cookie(name: String): String? =
        cookieHeader()?.split(";")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    fun token(): String? = cookie("session_token")

    fun xsrf(): String? = cookie("XSRF-TOKEN")

    val hasToken: Boolean get() = token() != null

    /** Sai da conta: apaga cookies e dados web do app. */
    fun logout() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
    }
}
