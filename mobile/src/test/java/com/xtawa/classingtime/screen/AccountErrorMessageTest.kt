package com.xtawa.classingtime.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.account.AccountApiException
import com.xtawa.classingtime.security.ClientSignatureException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountErrorMessageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun message(error: Throwable?) = accountErrorMessage(context, error, R.string.account_error_login_failed)

    private fun apiError(code: String, status: Int = 400) = AccountApiException(status, code, message = code)

    @Test
    fun clientSignatureExceptionIsExplained() {
        assertEquals(context.getString(R.string.account_error_client_signature), message(ClientSignatureException("rejected")))
        assertEquals(context.getString(R.string.account_error_client_signature), message(apiError("CLIENT_SIGNATURE_INVALID", 403)))
        assertEquals(context.getString(R.string.account_error_client_signature), message(apiError("CLIENT_MARKET_INVALID", 403)))
    }

    @Test
    fun accountServiceCodesMapToDedicatedStrings() {
        assertEquals(context.getString(R.string.account_error_market_mismatch), message(apiError("AUTH_MARKET_MISMATCH", 403)))
        assertEquals(context.getString(R.string.account_error_service_unavailable), message(apiError("CLIENT_SIGNATURE_POLICY_MISSING", 503)))
        assertEquals(context.getString(R.string.account_error_email_unchanged), message(apiError(AccountApiClient.ERROR_EMAIL_UNCHANGED, 200)))
        assertEquals(context.getString(R.string.account_error_username_invalid), message(apiError("ACCOUNT_USERNAME_INVALID")))
        assertEquals(context.getString(R.string.account_error_email_invalid), message(apiError("ACCOUNT_EMAIL_INVALID")))
        assertEquals(context.getString(R.string.account_error_invalid_credentials), message(apiError("AUTH_INVALID_CREDENTIALS", 401)))
    }

    @Test
    fun expiredHumanVerificationRequestsANewAttempt() {
        assertEquals(context.getString(R.string.account_turnstile_expired), message(apiError("AUTH_TURNSTILE_INVALID")))
        assertEquals(context.getString(R.string.account_turnstile_unavailable), message(apiError("AUTH_TURNSTILE_UNAVAILABLE", 503)))
    }

    @Test
    fun unknownErrorsFallBack() {
        assertEquals(context.getString(R.string.account_error_login_failed), message(apiError("SOMETHING_ELSE", 500)))
        assertEquals(context.getString(R.string.account_error_login_failed), message(java.io.IOException("offline")))
        assertEquals(context.getString(R.string.account_error_login_failed), message(null))
    }
}
