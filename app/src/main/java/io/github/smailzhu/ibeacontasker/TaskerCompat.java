package io.github.smailzhu.ibeacontasker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.net.URISyntaxException;

final class TaskerCompat {
    private static final String TAG = "TaskerCompat";
    private static final String BASE_KEY = "net.dinglisch.android.tasker";
    private static final String EXTRAS_PREFIX = BASE_KEY + ".extras.";
    private static final String EXTRA_VARIABLES_BUNDLE = EXTRAS_PREFIX + "VARIABLES";
    private static final String EXTRA_HOST_CAPABILITIES = EXTRAS_PREFIX + "HOST_CAPABILITIES";
    private static final String BUNDLE_KEY_RELEVANT_VARIABLES = BASE_KEY + ".RELEVANT_VARIABLES";

    private static final int EXTRA_HOST_CAPABILITY_SETTING_RETURN_VARIABLES = 2;
    private static final int EXTRA_HOST_CAPABILITY_RELEVANT_VARIABLES = 16;
    private static final int EXTRA_HOST_CAPABILITY_SETTING_SYNCHRONOUS_EXECUTION = 32;

    static void addVariableBundle(Bundle resultExtras, Bundle variables) {
        resultExtras.putBundle(EXTRA_VARIABLES_BUNDLE, variables);
    }

    static void addRelevantVariableList(Intent intentToHost, String[] variableNames) {
        intentToHost.putExtra(BUNDLE_KEY_RELEVANT_VARIABLES, variableNames);
    }

    static boolean hostSupportsRelevantVariables(Bundle extrasFromHost) {
        return hostSupports(extrasFromHost, EXTRA_HOST_CAPABILITY_RELEVANT_VARIABLES);
    }

    private static boolean hostSupports(Bundle extrasFromHost, int capability) {
        if (extrasFromHost == null) {
            return false;
        }
        return (extrasFromHost.getInt(EXTRA_HOST_CAPABILITIES, 0) & capability) == capability;
    }

    static final class Setting {
        static final String VARNAME_ERROR_MESSAGE = "%errmsg";
        static final int REQUESTED_TIMEOUT_MS_MAX = 3599000;
        static final int RESULT_CODE_OK = Activity.RESULT_OK;
        static final int RESULT_CODE_FAILED = Activity.RESULT_FIRST_USER + 1;
        static final int RESULT_CODE_PENDING = Activity.RESULT_FIRST_USER + 2;

        private static final String EXTRA_REQUESTED_TIMEOUT = EXTRAS_PREFIX + "REQUESTED_TIMEOUT";
        private static final String EXTRA_PLUGIN_COMPLETION_INTENT = EXTRAS_PREFIX + "COMPLETION_INTENT";
        private static final String EXTRA_RESULT_CODE = EXTRAS_PREFIX + "RESULT_CODE";
        private static final String EXTRA_CALL_SERVICE_PACKAGE = BASE_KEY + ".EXTRA_CALL_SERVICE_PACKAGE";
        private static final String EXTRA_CALL_SERVICE = BASE_KEY + ".EXTRA_CALL_SERVICE";
        private static final String EXTRA_CALL_SERVICE_FOREGROUND = BASE_KEY + ".EXTRA_CALL_SERVICE_FOREGROUND";

        static boolean hostSupportsVariableReturn(Bundle extrasFromHost) {
            return hostSupports(extrasFromHost, EXTRA_HOST_CAPABILITY_SETTING_RETURN_VARIABLES);
        }

        static boolean hostSupportsSynchronousExecution(Bundle extrasFromHost) {
            return hostSupports(extrasFromHost, EXTRA_HOST_CAPABILITY_SETTING_SYNCHRONOUS_EXECUTION);
        }

        static void requestTimeoutMS(Intent intentToHost, int timeoutMs) {
            int bounded = Math.max(0, Math.min(timeoutMs, REQUESTED_TIMEOUT_MS_MAX));
            intentToHost.putExtra(EXTRA_REQUESTED_TIMEOUT, bounded);
        }

        static boolean hasCompletionIntent(Intent originalFireIntent) {
            return completionIntentString(originalFireIntent) != null;
        }

        static String completionIntentString(Intent originalFireIntent) {
            return originalFireIntent == null
                    ? null
                    : originalFireIntent.getStringExtra(EXTRA_PLUGIN_COMPLETION_INTENT);
        }

        static Intent fireIntentForCompletionString(String completionIntentUri) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_PLUGIN_COMPLETION_INTENT, completionIntentUri);
            return intent;
        }

        static boolean signalFinish(Context context, Intent originalFireIntent, int resultCode, Bundle variables) {
            if (originalFireIntent == null) {
                return false;
            }

            String completionIntentUri = completionIntentString(originalFireIntent);
            if (completionIntentUri == null) {
                return false;
            }

            try {
                Intent completionIntent = Intent.parseUri(completionIntentUri, Intent.URI_INTENT_SCHEME);
                completionIntent.putExtra(EXTRA_RESULT_CODE, resultCode);
                if (variables != null) {
                    completionIntent.putExtra(EXTRA_VARIABLES_BUNDLE, variables);
                }

                String servicePackage = completionIntent.getStringExtra(EXTRA_CALL_SERVICE_PACKAGE);
                String serviceClass = completionIntent.getStringExtra(EXTRA_CALL_SERVICE);
                if (servicePackage != null && serviceClass != null
                        && completionIntent.hasExtra(EXTRA_CALL_SERVICE_FOREGROUND)) {
                    completionIntent.setComponent(new ComponentName(servicePackage, serviceClass));
                    boolean foreground = completionIntent.getBooleanExtra(EXTRA_CALL_SERVICE_FOREGROUND, false);
                    if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(completionIntent);
                    } else {
                        context.startService(completionIntent);
                    }
                } else {
                    context.sendBroadcast(completionIntent);
                }
                return true;
            } catch (URISyntaxException | RuntimeException ex) {
                Log.w(TAG, "Unable to signal Tasker completion", ex);
                return false;
            }
        }

        private Setting() {
        }
    }

    private TaskerCompat() {
    }
}
