package com.xtawa.classingtime.account

import android.content.Context
import com.xtawa.classingtime.BuildConfig

/** The installed package stays signed for its build flavor; the account realm is chosen separately. */
object AccountEdition {
    fun selected(context: Context): String = context.getSharedPreferences("account_edition", 0)
        .getString("selected", BuildConfig.CLIENT_MARKET).let { if (it == "GLOBAL") "GLOBAL" else "CN" }
    fun select(context: Context, market: String) {
        require(market == "CN" || market == "GLOBAL")
        context.getSharedPreferences("account_edition", 0).edit().putString("selected", market).apply()
    }
}
