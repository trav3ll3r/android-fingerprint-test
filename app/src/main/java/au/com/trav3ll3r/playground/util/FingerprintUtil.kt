package au.com.trav3ll3r.playground.util

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.preference.PreferenceManager
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import au.com.trav3ll3r.playground.R
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.spec.RSAKeyGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey

@SuppressLint("NewApi")
class FingerprintUtil(val context: Context) {

    companion object {
        private val TAG = FingerprintUtil::class.java.simpleName
        const val REVOCABLE_KEY_NAME = "revocable_key"
    }

    val cipher: Cipher by lazy {
        try {
            Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to get an instance of Cipher", e)
        } catch (e: NoSuchPaddingException) {
            throw RuntimeException("Failed to get an instance of Cipher", e)
        }
    }

    private val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService<KeyguardManager>(KeyguardManager::class.java)
    }

    private val fingerprintManager: FingerprintManager by lazy {
        context.getSystemService<FingerprintManager>(FingerprintManager::class.java)
    }

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance("AndroidKeyStore")
        } catch (e: KeyStoreException) {
            //throw RuntimeException("Failed to get an instance of KeyStore", e)
            null
        }
    }

    private val keyGeneratorSymmetric: KeyGenerator? by lazy {
        var keyGen: KeyGenerator? = null
        try {
            keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        } catch (e: NoSuchAlgorithmException) {
            //TODO("Failed to get an instance of KeyGenerator")
        } catch (e: NoSuchProviderException) {
            //TODO("Failed to get an instance of KeyGenerator")
        }
        keyGen
    }

    private val keyGeneratorAsymmetric: KeyPairGenerator? by lazy {
        var keyGen: KeyPairGenerator? = null
        try {
            // Set the alias of the entry in Android KeyStore where the key will appear
            keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        } catch (ex: NoSuchAlgorithmException) {
            Log.e(TAG, "NoSuchAlgorithmException", ex)
        } catch (ex: NoSuchProviderException) {
            Log.e(TAG, "NoSuchProviderException", ex)
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException", ex)
        }
        keyGen
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has authenticated with fingerprint.
     *
     * @param keyName                          the name of the key to be created
     *
     * @param invalidatedByBiometricEnrollment if `false` is passed, the created key will not
     *                                         be invalidated even if a new fingerprint is enrolled.
     *                                         The default value is `true`, so passing
     *                                         `true` doesn't change the behavior
     *                                         (the key will be invalidated if a new fingerprint is
     *                                         enrolled.). Note that this parameter is only valid if
     */
    @SuppressLint("NewApi")
    fun createKey(invalidatedByBiometricEnrollment: Boolean = true): Boolean {
//        val useAsymmetric = true
        val useAsymmetric = false

        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.

        // INIT keyStore
        try {
            keyStore?.load(null)
        } catch(ex: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException")
        } catch(ex: IOException) {
            Log.e(TAG, "IOException")
        } catch(ex: NoSuchAlgorithmException) {
            Log.e(TAG, "NoSuchAlgorithmException")
        } catch(ex: CertificateException) {
            Log.e(TAG, "CertificateException")
        }

//            try {
//                val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
//                val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
//                return keyPair.public != null
//            } catch (ex: Exception) {
//                // TODO: LOG
//            }
        try {
            if (useAsymmetric) {
                val keyGen = keyGeneratorAsymmetric
                if (keyGen != null) {
                    val builder = KeyPairGeneratorSpec.Builder(context)
                            .setAlias(REVOCABLE_KEY_NAME)
//                            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(1024, BigInteger("10000")))

//                    val rand = SecureRandom()
//                    val builder = KeyGenParameterSpec
//                            .Builder(REVOCABLE_KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
//                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
//                            // Require the user to authenticate with a fingerprint to authorize every use of the key
//                            .setUserAuthenticationRequired(true)
//                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
//                    // android.security.keystore.KeyGenParameterSpec or android.security.KeyPairGeneratorSpec required to initialize this KeyPairGenerator
//                    keyGen.initialize(builder)
                    keyGen.initialize(RSAKeyGenParameterSpec(1024, BigInteger("10000")))
                    val key = keyGen.genKeyPair()
                    return key != null
                }
                Log.d(TAG, "KeyGen = NULL")
            } else {
                val keyGen = keyGeneratorSymmetric
                // Set the constrains (purposes) in the constructor of the Builder
                val builder = KeyGenParameterSpec
                        .Builder(REVOCABLE_KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        // Require the user to authenticate with a fingerprint to authorize every use of the key
                        .setUserAuthenticationRequired(false)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)

                // This is a workaround to avoid crashes on devices whose API level is < 24
                // because KeyGenParameterSpec.Builder#setInvalidatedByBiometricEnrollment is only
                // visible on API level +24.
                // Ideally there should be a compat library for KeyGenParameterSpec.Builder but
                // which isn't available yet.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment)
                }

                if (keyGen != null) {
                    keyGen.init(builder.build())
                    val key = keyGen.generateKey()
                    return key != null
                }
                Log.d(TAG, "KeyGen = NULL")
            }

        } catch (ex: Exception) {
            Log.d(TAG, "KeyGen -> Exception:", ex)
        }
        return false
    }

    fun deleteKey(): Boolean {
        try {
            keyStore?.deleteEntry(REVOCABLE_KEY_NAME) // throws KeyStoreException
            return true
        } catch (ex: KeyStoreException) {
            return false
        }
    }

    val isKeyguardSecure: Boolean
        get() {
            return keyguardManager.isKeyguardSecure
        }

    val isFingerprintAuthAvailable: Boolean
        get() {
            return fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints()
        }

    val isFingerprintIsHardwareDetected: Boolean
        get() {
            return fingerprintManager.isHardwareDetected
        }

    val hasFingerprints: Boolean
        get() {
            return fingerprintManager.hasEnrolledFingerprints()
        }


    /**
     * Initialize the [Cipher] instance with the created key in the
     * [.createKey] method.

     * @param keyName the key name to init the cipher
     * *
     * @return `true` if initialization is successful, `false` if the lock screen has
     * * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * * the key was generated.
     */
    fun isKeyValid(): Boolean {
        val keyName = REVOCABLE_KEY_NAME
        if (isFingerprintAuthAvailable) {
            try {
                keyStore?.load(null) // throws java.io.IOException | java.security.NoSuchAlgorithmException | java.security.cert.CertificateException
                val key = keyStore?.getKey(keyName, null) as SecretKey? // throws java.security.KeyStoreException | java.security.NoSuchAlgorithmException | java.security.UnrecoverableKeyException
                cipher.init(Cipher.ENCRYPT_MODE, key)  // throws java.security.InvalidKeyException
                return true
                //        } catch (KeyPermanentlyInvalidatedException e) {
                //            return false;
            } catch (e: KeyStoreException) {
                //            throw new RuntimeException("Failed to init Cipher", e);
                return false
            } catch (e: CertificateException) {
                return false
            } catch (e: UnrecoverableKeyException) {
                return false
            } catch (e: IOException) {
                return false
            } catch (e: NoSuchAlgorithmException) {
                return false
            } catch (e: InvalidKeyException) {
                return false
            }
        }
        return false
    }

    fun keyExists(): Boolean {
        val keyName = REVOCABLE_KEY_NAME
        if (isFingerprintAuthAvailable) {
            try {
                keyStore?.load(null) // throws java.io.IOException | java.security.NoSuchAlgorithmException | java.security.cert.CertificateException
                val key = keyStore?.getKey(keyName, null) as SecretKey? // throws java.security.KeyStoreException | java.security.NoSuchAlgorithmException | java.security.UnrecoverableKeyException
                return key != null
            } catch (e: KeyStoreException) {
                //            throw new RuntimeException("Failed to init Cipher", e);
                return false
            } catch (e: CertificateException) {
                return false
            } catch (e: UnrecoverableKeyException) {
                return false
            } catch (e: IOException) {
                return false
            } catch (e: NoSuchAlgorithmException) {
                return false
            } catch (e: InvalidKeyException) {
                return false
            }
        }
        return false
    }

    val useFingerprint: Boolean
        get() {
            return sharedPreferences.getBoolean(context.getString(R.string.use_fingerprint_to_authenticate_key), false)
        }

}