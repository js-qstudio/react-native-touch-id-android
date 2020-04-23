package co.eleken.react_native_touch_id_android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.github.ajalt.reprint.core.AuthenticationFailureReason;
import com.github.ajalt.reprint.core.AuthenticationListener;
import com.github.ajalt.reprint.core.Reprint;

/**
 * Created by Eleken. on 16.03.17.
 */

public class FingerprintModule extends ReactContextBaseJavaModule {

    private WritableMap response;
    
    private final ReactApplicationContext mReactContext;
    
    FingerprintModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;
    }
    
    @Override
    public String getName() {
        return "Fingerprint";
    }
    
    @ReactMethod
    public void requestTouch() {
        
        response = Arguments.createMap();
        if (!isSensorAvailable()) {
            sendResponseEvent("failed", "Finger sensor is not available" );
            return;
        }
        
        Activity currentActivity = getCurrentActivity();
        
        if (currentActivity == null) {
            sendResponseEvent("failed", "Can't find current Activity" );
            return;
        }
        
        Reprint.authenticate(new AuthenticationListener() {

            @Override
            public void onSuccess(int moduleTag) {
                sendResponseEvent("ok", null );

            }
            @Override
            public void onFailure(final AuthenticationFailureReason failureReason, final boolean fatal,
                                  final CharSequence errorMessage, final int moduleTag, final int errorCode) {
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if(failureReason == AuthenticationFailureReason.LOCKED_OUT) {
                        final Thread t = new Thread(new Runnable() {
                            public void run() {
                                try {
                                    sendFailedResponseEvent("LOCKED_OUT", failureReason, fatal, moduleTag, errorCode );
                                } catch (Exception e) {
                                    Log.d("exceptionLog", errorMessage.toString());
                                }
                            }
                        });
                        t.start();
                    } else {
                        sendFailedResponseEvent( errorMessage.toString(), failureReason, fatal, moduleTag, errorCode );
                    }
                }
            }

        });
    }
    
    @ReactMethod
    public void dismiss() {
        Reprint.cancelAuthentication();
    }
    
    
    @ReactMethod
    public void isSensorAvailable(final Promise promise) {
        
        response = Arguments.createMap();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(mReactContext, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                sendResponse("failed", "You haven't allow this app to use your fingerprint sensor", promise);
                return;
            }
            
            if (mReactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) || isHardwareDetected() ) {
                if( hasEnrolledFingerprints() ) {
                    sendResponse("ok", null, promise);
                } else {
                    sendResponse("failed", "You have fingerprint sensor, but you should set it enabled in your settings to use with this app", promise);
                }
            } else {
                sendResponse("failed", "You don\'t have appropriate hardware", promise);
            }
        }
    }
    
    private boolean isSensorAvailable() {
        if (ActivityCompat.checkSelfPermission(mReactContext, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ( mReactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT )
                    || isHardwareDetected() );
    }

    private boolean isHardwareDetected() {
        FingerprintManager manager = (FingerprintManager) mReactContext.getSystemService(Context.FINGERPRINT_SERVICE);
        return manager != null && manager.isHardwareDetected();
    }

    private boolean hasEnrolledFingerprints() {
        FingerprintManager manager = (FingerprintManager) mReactContext.getSystemService(Context.FINGERPRINT_SERVICE);
        return manager != null && manager.hasEnrolledFingerprints();
    }
    
    private void sendResponse(String status, String message, Promise promise) {
        Reprint.cancelAuthentication();
        response = Arguments.createMap();
        response.putString("status", status);
        response.putString("error", message);
        promise.resolve(response);
    }

    private void sendResponseEvent(String status, String message) {
        Reprint.cancelAuthentication();

        response = Arguments.createMap();
        response.putString("status", status);
        response.putString("error", message);
        dispatchEvent( "FingerprintResponse", response );
    }

    private void sendFailedResponseEvent(String message, final AuthenticationFailureReason failureReason,
                                         final boolean fatal, int moduleTag, int errorCode )
    {
        if( fatal ) {
            Reprint.cancelAuthentication();
        }

        response = Arguments.createMap();
        response.putString("status", "failed");
        response.putString("error", message);
        if( failureReason != null ) {
            response.putString("failureReason", failureReason.toString());
        }
        response.putBoolean("fatal", fatal);
        response.putInt("moduleTag", moduleTag);
        response.putInt("errorCode", errorCode);
        dispatchEvent( "FingerprintResponse", response );
    }

    public void dispatchEvent(String eventName, ReadableMap params) {
        mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
