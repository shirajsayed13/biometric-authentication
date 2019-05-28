package com.example.biometricauthentication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * @author Shiraj Sayed
 */
public class MainActivity extends AppCompatActivity implements AuthListener {

    private static final String KEY_NAME = "authKey";
    private Cipher cipher;
    private KeyStore keyStore;

    private ImageView mFingerPrintImageView;
    private TextView mInstructionConsent;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    showCustomDialog();
                }
            }, 500);

            if (fingerprintManager != null && !fingerprintManager.isHardwareDetected()) {
                Toast.makeText(this, "FingerPrint Hardware Not Detected", Toast.LENGTH_LONG).show();
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "FingerPrint Permission not Granted", Toast.LENGTH_LONG).show();
            }

            if (fingerprintManager != null && !fingerprintManager.hasEnrolledFingerprints()) {
                Toast.makeText(this, "No FingerPrint Registered for this device," +
                        " Redirecting to Settings", Toast.LENGTH_LONG).show();
                startActivityForResult(new Intent(Settings.ACTION_SETTINGS), 0);
            }

            if (keyguardManager != null) {
                if (!keyguardManager.isKeyguardSecure()) {
                    Toast.makeText(this, "Please enable Lock Screen security " +
                            "on your devices to use FingerPrint Authentication, Redirecting to Settings", Toast.LENGTH_LONG).show();
                    startActivityForResult(new Intent(Settings.ACTION_SETTINGS), 0);
                } else {
                    try {
                        generateKey();
                    } catch (FingerPrintException e) {
                        e.printStackTrace();
                    }

                    if (initializeCipher()) {
                        FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);

                        FingerprintHandler helper = new FingerprintHandler(this);
                        helper.setAuthListener(this);
                        helper.startAuth(fingerprintManager, cryptoObject);
                    }
                }
            }

        } else {
            Toast.makeText(this, "Your Device do not support for Biometric Authentication." +
                    " Kindly use updated version", Toast.LENGTH_LONG).show();
        }
    }

    private void showCustomDialog() {
        ViewGroup viewGroup = findViewById(android.R.id.content);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.biometric_auth_dialogue,
                viewGroup, false);

        mFingerPrintImageView = dialogView.findViewById(R.id.image_view);
        mInstructionConsent = dialogView.findViewById(R.id.instruction_consent);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        dialog = builder.create();
        dialog.show();

        dialogView.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }


    private void generateKey() throws FingerPrintException {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

                keyStore.load(null);
                keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setUserAuthenticationRequired(true)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());

                keyGenerator.generateKey();
            }
        } catch (KeyStoreException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | CertificateException
                | IOException
                | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            throw new FingerPrintException(e);
        }

    }


    public boolean initializeCipher() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cipher = Cipher.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES + "/"
                                + KeyProperties.BLOCK_MODE_CBC + "/"
                                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            }

        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (InvalidKeyException e) {
            return false;
        } catch (IOException
                | CertificateException
                | NoSuchAlgorithmException
                | UnrecoverableKeyException
                | KeyStoreException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }


    @Override
    public void getResultType(FingerprintHandler.ResultType result) {
        switch (result) {
            case FAILURE:
                mInstructionConsent.setText(getString(R.string.not_recognized));
                mFingerPrintImageView.setImageDrawable(getDrawable(R.drawable.ic_not_recognized));
                break;

            default:
            case AUTHENTICATION_HELP:
                mInstructionConsent.setText(getString(R.string.help_required));
                mFingerPrintImageView.setImageDrawable(getDrawable(R.drawable.ic_help_outline));
                break;

            case AUTHENTICATION_ERROR:
                mInstructionConsent.setText(getString(R.string.error));
                mFingerPrintImageView.setImageDrawable(getDrawable(R.drawable.ic_error_outline));
                break;

            case SUCCESS:
                mInstructionConsent.setText(getString(R.string.success));
                mFingerPrintImageView.setImageDrawable(getDrawable(R.drawable.ic_thumb_up));
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dialog != null)
            dialog.dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialog != null)
            dialog.dismiss();
    }

    private class FingerPrintException extends Exception {
        FingerPrintException(Exception e) {
            super(e);
        }
    }
}
