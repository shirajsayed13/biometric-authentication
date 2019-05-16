package com.example.biometricauthentication;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.support.v4.app.ActivityCompat;

@TargetApi(Build.VERSION_CODES.M)
public class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

    private AuthListener authListener;
    private Context context;

    FingerprintHandler(Context context) {
        this.context = context;
    }

    void setAuthListener(AuthListener listener) {
        this.authListener = listener;
    }

    void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject) {
        CancellationSignal cancellationSignal = new CancellationSignal();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        authListener.getResultType(ResultType.AUTHENTICATION_ERROR);
    }

    @Override
    public void onAuthenticationFailed() {
        authListener.getResultType(ResultType.FAILURE);
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        authListener.getResultType(ResultType.AUTHENTICATION_HELP);
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        authListener.getResultType(ResultType.SUCCESS);
    }

    enum ResultType {SUCCESS, FAILURE, AUTHENTICATION_ERROR, AUTHENTICATION_HELP}
}
