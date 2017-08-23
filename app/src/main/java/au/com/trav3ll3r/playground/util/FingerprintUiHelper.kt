package au.com.trav3ll3r.playground.util

import android.annotation.SuppressLint
import android.hardware.fingerprint.FingerprintManager
import android.os.CancellationSignal
import android.widget.ImageView
import android.widget.TextView

import au.com.trav3ll3r.playground.R

/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
@SuppressLint("NewApi")
class FingerprintUiHelper
/**
 * Constructor for [FingerprintUiHelper].
 */
internal constructor(private val fingerprintManager: FingerprintManager, private val icon: ImageView, private val errorTextView: TextView, private val authCallback: FingerprintAuthCallback) : FingerprintManager.AuthenticationCallback() {

    companion object {
        private val ERROR_TIMEOUT_MILLIS: Long = 1600
        private val SUCCESS_DELAY_MILLIS: Long = 1300
    }

    interface FingerprintAuthCallback {
        fun onAuthenticated()
        fun onError()
    }

    private var cancellationSignal: CancellationSignal? = null

    private var selfCancelled: Boolean = false

    fun startListening(cryptoObject: FingerprintManager.CryptoObject?) {
        if (!(fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints())) {
            return
        }
        cancellationSignal = CancellationSignal()
        selfCancelled = false

        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0 /* flags */, this, null)
        icon.setImageResource(R.drawable.ic_fp_40px)
    }

    fun stopListening() {
        val cancelled = cancellationSignal
        if (cancelled != null) {
            selfCancelled = true
            cancelled.cancel()
            cancellationSignal = null
        }
    }

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (!selfCancelled) {
            showError(errString)
            icon.postDelayed({ authCallback.onError() }, ERROR_TIMEOUT_MILLIS)
        }
    }

    override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
        showError(helpString)
    }

    override fun onAuthenticationFailed() {
        showError(icon.resources.getString(
                R.string.fingerprint_not_recognized))
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
        errorTextView.removeCallbacks(mResetErrorTextRunnable)
        icon.setImageResource(R.drawable.ic_fingerprint_success)
        errorTextView.setTextColor(errorTextView.resources.getColor(R.color.success_color, null))
        errorTextView.text = errorTextView.resources.getString(R.string.fingerprint_success)
        icon.postDelayed({ authCallback.onAuthenticated() }, SUCCESS_DELAY_MILLIS)
    }

    private fun showError(error: CharSequence) {
        icon.setImageResource(R.drawable.ic_fingerprint_error)
        errorTextView.text = error
        errorTextView.setTextColor(errorTextView.resources.getColor(R.color.error_color, null))
        errorTextView.removeCallbacks(mResetErrorTextRunnable)
        errorTextView.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS)
    }

    private val mResetErrorTextRunnable = Runnable {
        errorTextView.setTextColor(errorTextView.resources.getColor(R.color.hint_color, null))
        errorTextView.text = errorTextView.resources.getString(R.string.fingerprint_hint)
        icon.setImageResource(R.drawable.ic_fp_40px)
    }
}