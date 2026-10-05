package com.xtawa.classingtime.screen

import java.net.URI

internal fun registrationVerificationOrigin(apiBaseUrl: String): String {
    val uri = URI(apiBaseUrl)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "Human verification requires an HTTPS API origin"
    }
    return URI(uri.scheme, null, uri.host, uri.port, "/", null, null).toASCIIString()
}

internal fun turnstilePage(siteKey: String, dark: Boolean): String {
    require(siteKey.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid verification site key" }
    val theme = if (dark) "dark" else "light"
    // Compact is 150x140 CSS pixels and fits narrow dialogs without scaling the widget.
    return """<!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;background:transparent}body{display:flex;justify-content:center;padding:8px 0}</style>
        </head><body><div id="verification"></div><script>
        function report(kind,value){ClassingNative.event(kind,value||'');}
        function startVerification(){
          try {
            turnstile.render('#verification',{
              sitekey:'$siteKey',size:'compact',theme:'$theme',retry:'never',
              callback:function(token){report('verified',token);},
              'error-callback':function(code){report('error',String(code));return true;},
              'expired-callback':function(){report('expired');},
              'timeout-callback':function(){report('timeout');},
              'refresh-expired':'never','refresh-timeout':'never'
            });
            report('ready');
          } catch(e){report('error','render');}
        }
        </script><script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=startVerification&amp;render=explicit"
        onerror="report('error','script')" async defer></script></body></html>""".trimIndent()
}

/** All callbacks arrive on the main thread. Failed/disposed attempts cannot submit tokens. */
internal class TurnstileAttempt {
    private var active = true
    fun finish(): Boolean {
        if (!active) return false
        active = false
        return true
    }
    fun dispose() { active = false }
    fun isActive(): Boolean = active
}

internal fun verificationNavigationAllowed(url: String, origin: String): Boolean = runCatching {
    if (url == "about:blank" || url == "about:srcdoc") return@runCatching true
    val target = URI(url)
    val base = URI(origin)
    target.scheme == "https" && target.userInfo == null &&
        ((target.host == base.host && target.port == base.port) ||
            (target.host == "challenges.cloudflare.com" && target.port in listOf(-1, 443)))
}.getOrDefault(false)
