package au.com.trav3ll3r.playground.fingerprint

import android.annotation.SuppressLint
import android.app.DialogFragment
import android.content.Context
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import au.com.trav3ll3r.playground.fingerprint.FingerprintActivity
import au.com.trav3ll3r.playground.R
import au.com.trav3ll3r.playground.util.FingerprintUiHelper
import au.com.trav3ll3r.playground.util.FingerprintUtil

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
@SuppressLint("NewApi")
class FingerprintAuthenticationDialogFragment : DialogFragment(), TextView.OnEditorActionListener, FingerprintUiHelper.FingerprintAuthCallback {

    private lateinit var btnCancel: Button
    private lateinit var backupReason: TextView
    private lateinit var btnSecondOption: Button
    private lateinit var fingerprintContent: View
    private lateinit var backupContent: View
    private lateinit var passwordField: EditText
    private lateinit var useFingerprintFutureCheckBox: CheckBox
    private lateinit var passwordDescriptionTextView: TextView
    private lateinit var newFingerprintEnrolledTextView: TextView

    private var stage = Stage.FINGERPRINT

    private lateinit var activity: FingerprintActivity

    private var cryptoObject: FingerprintManager.CryptoObject? = null

    private lateinit var fingerprintUiHelper: FingerprintUiHelper
    private val fingerprintUtil: FingerprintUtil by lazy { FingerprintUtil(context) }
    private val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        retainInstance = true
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog.setTitle(getString(R.string.sign_in))
        val v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false)
        btnCancel = v.findViewById<Button>(R.id.cancel_button)
        backupReason = v.findViewById<TextView>(R.id.use_backup_reason)
        btnSecondOption = v.findViewById<Button>(R.id.second_dialog_button)
        fingerprintContent = v.findViewById<View>(R.id.fingerprint_container)
        backupContent = v.findViewById<View>(R.id.backup_container)
        passwordField = v.findViewById<EditText>(R.id.password)
        passwordDescriptionTextView = v.findViewById<TextView>(R.id.password_description)
        useFingerprintFutureCheckBox = v.findViewById<CheckBox>(R.id.use_fingerprint_in_future_check)
        newFingerprintEnrolledTextView = v.findViewById<TextView>(R.id.new_fingerprint_enrolled_description)

        btnCancel.setOnClickListener { dismiss() }
        btnSecondOption.setOnClickListener {
            if (stage == Stage.FINGERPRINT) {
                goToBackup("User decided to authenticate with Password")
            } else {
                verifyPassword()
            }
        }
        passwordField.setOnEditorActionListener(this)
        fingerprintUiHelper = FingerprintUiHelper(activity.getSystemService(FingerprintManager::class.java),
                v.findViewById<View>(R.id.fingerprint_icon) as ImageView,
                v.findViewById<View>(R.id.fingerprint_status) as TextView, this)
        updateStage()

        // If fingerprint authentication is not available, switch immediately to the backup
        // (password) screen.
        if (!fingerprintUtil.isFingerprintAuthAvailable) {
            goToBackup("No Fingerprints found")
        }
        return v
    }

    override fun onResume() {
        super.onResume()
        if (stage == Stage.FINGERPRINT) {
            fingerprintUiHelper.startListening(cryptoObject)
        }
    }

    fun setStage(stage: Stage) {
        this.stage = stage
    }

    override fun onPause() {
        super.onPause()
        fingerprintUiHelper.stopListening()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as FingerprintActivity
    }

    /**
     * Sets the crypto object to be passed in when authenticating with fingerprint.
     */
    fun setCryptoObject(cryptoObject: FingerprintManager.CryptoObject) {
        this.cryptoObject = cryptoObject
    }

    /**
     * Switches to backup (password) screen. This either can happen when fingerprint is not
     * available or the user chooses to use the password authentication method by pressing the
     * button. This can also happen when the user had too many fingerprint attempts.
     */
    private fun goToBackup(reason: String) {
        stage = Stage.PASSWORD
        backupReason.text = reason
        updateStage()
        passwordField.requestFocus()

        // Fingerprint is not used anymore. Stop listening for it.
        fingerprintUiHelper.stopListening()
    }

    /**
     * Checks whether the current entered password is correct, and dismisses the the dialog and
     * let's the activity know about the result.
     */
    private fun verifyPassword() {
        if (!checkPassword(passwordField.text.toString())) {
            return
        }
//        if (stage == Stage.NEW_FINGERPRINT_ENROLLED) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(getString(R.string.use_fingerprint_to_authenticate_key), useFingerprintFutureCheckBox.isChecked)
        editor.apply()

        if (useFingerprintFutureCheckBox.isChecked) {
            // Re-create the key so that fingerprints including new ones are validated.
            fingerprintUtil.createKey(true)
            stage = Stage.FINGERPRINT
        }
//        }
        passwordField.setText("")
        activity.onLoginSuccess(false /* without Fingerprint */, null)
        dismiss()
    }

    /**
     * @return true if `password` is correct, false otherwise
     */
    private fun checkPassword(password: String): Boolean {
        // Assume the password is always correct.
        // In the real world situation, the password needs to be verified in the server side.
        return password.isNotEmpty()
    }

    private fun updateStage() {
        when (stage) {
            FingerprintAuthenticationDialogFragment.Stage.FINGERPRINT -> {
                btnCancel.setText(R.string.cancel)
                btnSecondOption.setText(R.string.use_password)
                fingerprintContent.visibility = View.VISIBLE
                backupContent.visibility = View.GONE
            }
            FingerprintAuthenticationDialogFragment.Stage.NEW_FINGERPRINT_ENROLLED, // Intentional fall through
            FingerprintAuthenticationDialogFragment.Stage.PASSWORD -> {
                btnCancel.setText(R.string.cancel)
                btnSecondOption.setText(R.string.ok)
                fingerprintContent.visibility = View.GONE
                backupContent.visibility = View.VISIBLE
                if (stage == Stage.NEW_FINGERPRINT_ENROLLED) {
                    passwordDescriptionTextView.visibility = View.GONE
                    newFingerprintEnrolledTextView.visibility = View.VISIBLE
                    useFingerprintFutureCheckBox.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            verifyPassword()
            return true
        }
        return false
    }

    override fun onAuthenticated() {
        // Callback from FingerprintUiHelper. Let the activity know that authentication was
        // successful.
        activity.onLoginSuccess(true /* withFingerprint */, cryptoObject)
        dismiss()
    }

    override fun onError() {
        goToBackup("Error occurred")
    }

    /**
     * Enumeration to indicate which authentication method the user is trying to authenticate with.
     */
    enum class Stage {
        FINGERPRINT,
        NEW_FINGERPRINT_ENROLLED,
        PASSWORD
    }
}