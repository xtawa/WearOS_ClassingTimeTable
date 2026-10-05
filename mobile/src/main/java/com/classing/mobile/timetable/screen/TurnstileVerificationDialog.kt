package com.xtawa.classingtime.screen

import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.net.http.SslError
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import kotlinx.coroutines.delay

@Composable
internal fun TurnstileVerificationDialog(
    siteKey: String,
    onVerified: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var retry by remember(siteKey) { mutableStateOf(0) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val attempt = remember(siteKey, retry, dark) { TurnstileAttempt() }
    var loading by remember(attempt) { mutableStateOf(true) }
    var failure by remember(attempt) { mutableStateOf<String?>(null) }
    val verified by rememberUpdatedState(onVerified)
    val dismiss by rememberUpdatedState(onDismiss)
    val unavailable = stringResource(R.string.account_turnstile_load_failed)
    val expired = stringResource(R.string.account_turnstile_expired)
    val origin = remember { runCatching { registrationVerificationOrigin(AccountApiClient.BASE_URL) }.getOrNull() }
    val html = remember(siteKey, dark) { runCatching { turnstilePage(siteKey, dark) }.getOrNull() }
    val uriHandler = LocalUriHandler.current
    fun fail(message: String) {
        if (attempt.finish()) {
            loading = false
            failure = message
        }
    }
    DisposableEffect(attempt) { onDispose { attempt.dispose() } }
    LaunchedEffect(attempt) {
        if (origin == null || html == null) fail(unavailable)
        else {
            delay(30_000)
            if (loading) fail(unavailable)
        }
    }
    AlertDialog(
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = { attempt.dispose(); dismiss() },
        title = { Text(stringResource(R.string.account_turnstile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
                Text(stringResource(R.string.account_turnstile_desc), style = MaterialTheme.typography.bodyMedium)
                if (failure != null) {
                    Text(failure.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                } else if (origin != null && html != null) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    key(attempt, dark) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(172.dp),
                            factory = { context ->
                                WebView(context).apply {
                                    setBackgroundColor(AndroidColor.TRANSPARENT)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    // Turnstile maintains state inside its cross-origin challenge frame.
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                            handler.cancel()
                                            fail(unavailable)
                                        }
                                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                            fail(unavailable)
                                            return true
                                        }
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                            !verificationNavigationAllowed(request.url.toString(), origin)
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                            if (request.isForMainFrame || request.url.toString().startsWith("https://challenges.cloudflare.com/turnstile/v0/api.js")) fail(unavailable)
                                        }
                                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                            if (request.isForMainFrame || request.url.toString().startsWith("https://challenges.cloudflare.com/turnstile/v0/api.js")) fail(unavailable)
                                        }
                                    }
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun event(kind: String, value: String) {
                                            post {
                                                if (!attempt.isActive()) return@post
                                                when (kind) {
                                                    "ready" -> loading = false
                                                    "verified" -> if (value.isNotBlank() && attempt.finish()) verified(value)
                                                    "expired", "timeout" -> fail(expired)
                                                    "error" -> {
                                                        // Only numeric diagnostic codes are displayed; never tokens or arbitrary JS text.
                                                        val code = value.takeIf { it.matches(Regex("[0-9]{3,6}")) }
                                                        fail(if (code == null) unavailable else "$unavailable ($code)")
                                                    }
                                                }
                                            }
                                        }
                                    }, "ClassingNative")
                                    loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
                                }
                            },
                            onRelease = { view ->
                                view.stopLoading()
                                view.removeJavascriptInterface("ClassingNative")
                                view.removeAllViews()
                                view.destroy()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (failure != null) TextButton(onClick = { retry++ }) {
                Text(stringResource(R.string.account_turnstile_retry))
            }
        },
        dismissButton = {
            Column {
                if (failure != null && origin != null) TextButton(onClick = {
                    runCatching { uriHandler.openUri(origin) }
                    attempt.dispose()
                    dismiss()
                }) { Text(stringResource(R.string.account_turnstile_web_register)) }
                TextButton(onClick = { attempt.dispose(); dismiss() }) {
                    Text(stringResource(R.string.settings_about_update_close))
                }
            }
        },
    )
}
