package au.com.trav3ll3r.playground.fingerprint

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import au.com.trav3ll3r.playground.R
import au.com.trav3ll3r.playground.bottomsheet.BottomSheetActivity
import au.com.trav3ll3r.playground.fingerprint.FingerprintAuthenticationDialogFragment
import au.com.trav3ll3r.playground.fingerprint.FingerprintStatus
import au.com.trav3ll3r.playground.fingerprint.FingerprintStatusAdapter
import au.com.trav3ll3r.playground.util.FingerprintUtil
import org.jetbrains.anko.find
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException

@SuppressLint("NewApi")
class FingerprintActivity : AppCompatActivity() {

    companion object {
        private val TAG = FingerprintActivity::class.java.simpleName

        private val DIALOG_FRAGMENT_TAG = "myFragment"
        private val SECRET_MESSAGE = "Very secret message"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FingerprintStatusAdapter
    private lateinit var btnLogin: Button
    private lateinit var btnRefreshStatus: Button
    private lateinit var btnCreateKey: Button
    private lateinit var btnDeleteKey: Button
    private lateinit var btnSecuritySettings: Button
    private lateinit var btnBottomSheet: Button

    private lateinit var createMessage: TextView
    private lateinit var deleteMessage: TextView
    private lateinit var loginMessage: TextView

    private val fingerprintUtil = FingerprintUtil(this)

    internal var createKeyListener: View.OnClickListener = View.OnClickListener {
        val fingerprintKeyCreated = fingerprintUtil.createKey(true)
        Log.d(TAG, String.format("onCheckedChanged: createKey=%s", fingerprintKeyCreated))
        if (fingerprintKeyCreated) {
            createMessage.text = "Created"
            toggleRefreshRequired(true)
        } else {
            createMessage.text = "Failed to create Fingerprint key"
        }
        createMessage.visibility = View.VISIBLE
    }

    internal var deleteKeyListener: View.OnClickListener = View.OnClickListener {
        val fingerprintKeyDeleted = fingerprintUtil.deleteKey()
        Log.d(TAG, String.format("onCheckedChanged: deleteKey=%s", fingerprintKeyDeleted))
        if (fingerprintKeyDeleted) {
            deleteMessage.text = "Removed"
            toggleRefreshRequired(true)
        } else {
            deleteMessage.text = "Failed to remove Fingerprint key"
        }
        deleteMessage.visibility = View.VISIBLE
    }

    private fun toggleRefreshRequired(refreshRequired: Boolean = false) {
        val refreshIcon = find<ImageView>(R.id.requires_refresh)

        if (refreshRequired) {
            refreshIcon.visibility = View.VISIBLE
        } else {
            refreshIcon.visibility = View.GONE
            clearAllMessages()
        }
    }

    private fun clearAllMessages() {
        loginMessage.visibility = View.GONE
        createMessage.visibility = View.GONE
        deleteMessage.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        btnLogin = findViewById<Button>(R.id.button_login)
        btnRefreshStatus = findViewById<Button>(R.id.button_refresh_status)
        btnCreateKey = findViewById<Button>(R.id.button_create_key)
        btnDeleteKey = findViewById<Button>(R.id.button_delete_key)
        btnSecuritySettings = findViewById<Button>(R.id.button_security_settings)
        btnBottomSheet = findViewById<Button>(R.id.button_start_bottom_sheet)

        createMessage = find<TextView>(R.id.create_key_message)
        deleteMessage = find<TextView>(R.id.delete_key_message)
        loginMessage = find<TextView>(R.id.login_message)

        btnRefreshStatus.setOnClickListener {
            updateRecyclerView()
        }

        btnBottomSheet.setOnClickListener {
            startActivity(Intent(this, BottomSheetActivity::class.java))
        }

        initRecyclerView()
    }

    private fun initRecyclerView() {
        adapter = FingerprintStatusAdapter(getFingerprintStatus())

        // use a grid layout manager with 1 column
        val layoutManager = GridLayoutManager(this, 1)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, layoutManager.orientation)

        recyclerView.hasFixedSize()
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        enableButtons()
    }

    private fun updateRecyclerView() {
        adapter.update(getFingerprintStatus())
        toggleRefreshRequired()
    }

    private fun getFingerprintStatus(): MutableList<FingerprintStatus> {
        val keyguardSecure = fingerprintUtil.isKeyguardSecure
        val teeKeyValid = fingerprintUtil.isKeyValid()
        val teeKeyFound = fingerprintUtil.keyExists()

        return mutableListOf(
                FingerprintStatus("Hardware Detected", fingerprintUtil.isFingerprintIsHardwareDetected),
                FingerprintStatus("Has Fingerprints", fingerprintUtil.hasFingerprints),
                FingerprintStatus("Keyguard Secure", keyguardSecure, if (keyguardSecure) "" else "Secure lock screen must be one of: PIN, Password or Pattern"),
                FingerprintStatus("TEE integrity", teeKeyValid, if (teeKeyValid) "OK" else "Fingerprint (TEE) was modified"),
                FingerprintStatus("TEE key", teeKeyFound, if (teeKeyFound) "Key found" else "Key NOT found")
        )
    }

    private fun enableButtons() {
        // LOGIN BUTTON
        btnLogin.isEnabled = true
        btnLogin.setOnClickListener(LoginButtonClickListener())

        btnCreateKey.setOnClickListener(createKeyListener)
        btnDeleteKey.setOnClickListener(deleteKeyListener)

        btnSecuritySettings.setOnClickListener {
            // OPEN DEVICE SECURITY SETTING (MANAGE DEVICE'S FINGERPRINTS)
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    /**
     * Proceed the purchase operation

     * @param withFingerprint `true` if the purchase was made by using a fingerprint
     * *
     * @param cryptoObject    the Crypto object
     */
    fun onLoginSuccess(withFingerprint: Boolean, cryptoObject: FingerprintManager.CryptoObject?) {
        if (withFingerprint) {
            // If the user has authenticated with fingerprint, verify that using cryptography and
            // then show the confirmation message.
            assert(cryptoObject != null)
            tryEncrypt(cryptoObject!!.cipher)
        } else {
            // Authentication happened with backup password. Just show the confirmation message.
            showConfirmation(null)
            toggleRefreshRequired(true)
        }
    }

    // Show confirmation, if fingerprint was used show crypto information.
    private fun showConfirmation(encrypted: ByteArray?) {
        loginMessage.visibility = View.VISIBLE
//        if (encrypted != null) {
//            val v = findViewById<TextView>(R.id.encrypted_message)
//            v.visibility = View.VISIBLE
//            v.text = Base64.encodeToString(encrypted, 0 /* flags */)
//        }
    }

    /**
     * Tries to encrypt some data with the generated key in [FingerprintUtil.createKey] which is
     * only works if the user has just authenticated via fingerprint.
     */
    private fun tryEncrypt(cipher: Cipher) {
        try {
            val encrypted = cipher.doFinal(SECRET_MESSAGE.toByteArray())
            showConfirmation(encrypted)
        } catch (e: BadPaddingException) {
            Toast.makeText(this, "Failed to encrypt the data with the generated key. Retry the purchase", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to encrypt the data with the generated key." + e.message)
        } catch (e: IllegalBlockSizeException) {
            Toast.makeText(this, "Failed to encrypt the data with the generated key. Retry the purchase", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to encrypt the data with the generated key." + e.message)
        }
    }

    private inner class LoginButtonClickListener internal constructor() : View.OnClickListener {

        internal var cipher: Cipher = fingerprintUtil.cipher

        override fun onClick(view: View) {
//            findViewById<View>(R.id.confirmation_message).visibility = View.GONE
//            findViewById<View>(R.id.encrypted_message).visibility = View.GONE

            // Set up the crypto object for later. The object will be authenticated by use
            // of the fingerprint.
            if (fingerprintUtil.isKeyValid()) {
                // Show the fingerprint dialog. The user has the option to use the fingerprint with
                // crypto, or you can fall back to using a server-side verified password.
                val fragment = FingerprintAuthenticationDialogFragment()
                fragment.setCryptoObject(FingerprintManager.CryptoObject(cipher))
                var dialogStage = FingerprintAuthenticationDialogFragment.Stage.PASSWORD
                if (fingerprintUtil.useFingerprint) {
                    dialogStage = FingerprintAuthenticationDialogFragment.Stage.FINGERPRINT
                }
                fragment.setStage(dialogStage)
                fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG)
            } else {
                // This happens if the lock screen has been disabled or or a fingerprint got
                // enrolled. Thus show the dialog to authenticate with their password first
                // and ask the user if they want to authenticate with fingerprints in the
                // future
                val fragment = FingerprintAuthenticationDialogFragment()
                fragment.setCryptoObject(FingerprintManager.CryptoObject(cipher))
                fragment.setStage(FingerprintAuthenticationDialogFragment.Stage.NEW_FINGERPRINT_ENROLLED)
                fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG)
            }
        }
    }
}