package io.github.smailzhu.ibeacontasker;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;
    private static final int COLOR_PRIMARY = Color.rgb(0, 107, 95);
    private static final int COLOR_ON_PRIMARY = Color.WHITE;

    private EditText uuidField;
    private EditText majorField;
    private EditText minorField;
    private EditText durationField;
    private EditText minRssiField;
    private TextView statusView;
    private TextView resultsView;
    private Button permissionButton;
    private Button backgroundPermissionButton;
    private TextView backgroundPermissionHint;
    private Button scanButton;
    private Button monitorButton;
    private IBeaconScanner scanner;
    private final StringBuilder scanLog = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        fillFromConfig(ScanConfig.defaults());
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS
                || requestCode == REQUEST_BACKGROUND_LOCATION
                || requestCode == REQUEST_NOTIFICATIONS) {
            updateStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        SystemBarPadding.apply(container, dp(20), dp(20), dp(20), dp(28));
        scrollView.addView(container);

        TextView title = new TextView(this);
        title.setText("iBeacon Tasker");
        title.setTextSize(26);
        title.setGravity(Gravity.START);
        container.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Bluetooth LE iBeacon scanner for Tasker");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        container.addView(subtitle, matchWrap());

        uuidField = textField("UUID, blank for any", InputType.TYPE_CLASS_TEXT);
        majorField = textField("Major, blank for any", InputType.TYPE_CLASS_NUMBER);
        minorField = textField("Minor, blank for any", InputType.TYPE_CLASS_NUMBER);
        durationField = textField("Duration seconds", InputType.TYPE_CLASS_NUMBER);
        minRssiField = textField("Minimum RSSI, optional", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        addLabeledField(container, "UUID", uuidField);
        addLabeledField(container, "Major", majorField);
        addLabeledField(container, "Minor", minorField);
        addLabeledField(container, "Duration", durationField);
        addLabeledField(container, "Minimum RSSI", minRssiField);

        permissionButton = button("Grant permissions");
        permissionButton.setOnClickListener(v -> requestPermissions(
                PermissionUtils.appRuntimePermissions(),
                REQUEST_PERMISSIONS));
        container.addView(permissionButton, matchWrapWithTop(12));

        if (PermissionUtils.needsBackgroundLocationPermission()) {
            backgroundPermissionButton = button("Background location settings");
            backgroundPermissionButton.setOnClickListener(v -> requestBackgroundLocation());
            container.addView(backgroundPermissionButton, matchWrapWithTop(8));

            backgroundPermissionHint = new TextView(this);
            backgroundPermissionHint.setText(PermissionUtils.backgroundLocationSettingsHint(this));
            backgroundPermissionHint.setTextSize(12);
            container.addView(backgroundPermissionHint, matchWrapWithTop(4));
        }

        scanButton = button("Start scan");
        scanButton.setTextColor(COLOR_ON_PRIMARY);
        scanButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
        scanButton.setOnClickListener(v -> toggleScan());
        container.addView(scanButton, matchWrapWithTop(8));

        monitorButton = button("");
        monitorButton.setOnClickListener(v -> toggleMonitor());
        container.addView(monitorButton, matchWrapWithTop(8));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(16), 0, dp(8));
        container.addView(statusView, matchWrap());

        resultsView = new TextView(this);
        resultsView.setTextSize(14);
        resultsView.setVisibility(View.GONE);
        container.addView(resultsView, matchWrap());
        return scrollView;
    }

    private void toggleMonitor() {
        if (BeaconMonitorService.isRunning(this)) {
            BeaconMonitorService.stop(this);
            updateStatus();
            return;
        }

        if (!PermissionUtils.hasNotificationPermission(this)) {
            requestPermissions(
                    PermissionUtils.notificationRuntimePermissions(),
                    REQUEST_NOTIFICATIONS);
            return;
        }

        String readinessProblem = PermissionUtils.monitorReadinessProblem(this);
        if (readinessProblem != null) {
            Toast.makeText(this, readinessProblem, Toast.LENGTH_LONG).show();
            updateStatus();
            return;
        }

        try {
            BeaconMonitorService.start(this);
            monitorButton.postDelayed(this::updateStatus, 500);
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Unable to start compatibility monitor", Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void requestBackgroundLocation() {
        if (!PermissionUtils.needsBackgroundLocationPermission()) {
            updateStatus();
            return;
        }
        if (!PermissionUtils.hasScanPermissions(this)) {
            Toast.makeText(this, "Grant scan permissions first", Toast.LENGTH_LONG).show();
            return;
        }
        if (PermissionUtils.hasBackgroundLocationPermission(this)) {
            Toast.makeText(this, "Background location already granted", Toast.LENGTH_SHORT).show();
            updateStatus();
            return;
        }
        if (PermissionUtils.canRequestBackgroundLocationDirectly()) {
            requestPermissions(
                    PermissionUtils.backgroundLocationRuntimePermissions(),
                    REQUEST_BACKGROUND_LOCATION);
        } else {
            Toast.makeText(
                    this,
                    PermissionUtils.backgroundLocationSettingsHint(this),
                    Toast.LENGTH_LONG).show();
            startActivity(PermissionUtils.appSettingsIntent(this));
        }
    }

    private void toggleScan() {
        if (scanner != null) {
            scanner.stop();
            scanner = null;
            scanButton.setText("Start scan");
            return;
        }

        ScanConfig config = readConfigOrShowError();
        if (config == null) {
            return;
        }

        String readinessProblem = PermissionUtils.scanReadinessProblem(this);
        if (readinessProblem != null) {
            Toast.makeText(this, readinessProblem, Toast.LENGTH_LONG).show();
            updateStatus();
            return;
        }

        scanLog.setLength(0);
        showResults("Scanning...");
        scanButton.setText("Stop scan");
        scanner = new IBeaconScanner(this, config, new IBeaconScanner.Listener() {
            @Override
            public void onBeacon(IBeacon beacon) {
                scanLog.append(beacon.displayLine()).append('\n');
                showResults(scanLog.toString());
            }

            @Override
            public void onCompleted(IBeacon bestBeacon) {
                scanner = null;
                scanButton.setText("Start scan");
                if (bestBeacon == null) {
                    showResults("No matching iBeacon found");
                } else {
                    showResults("Best match\n"
                            + bestBeacon.displayLine()
                            + "\n\nSeen\n"
                            + scanLog);
                }
            }

            @Override
            public void onError(String message) {
                scanner = null;
                scanButton.setText("Start scan");
                showResults(message);
                updateStatus();
            }
        });
        scanner.start();
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        updatePermissionControls();
        String problem = PermissionUtils.scanReadinessProblem(this);
        if (problem == null) {
            problem = PermissionUtils.taskerScanReadinessProblem(this);
        }
        boolean monitorRunning = BeaconMonitorService.isRunning(this);
        if (monitorButton != null) {
            monitorButton.setText(monitorRunning
                    ? "Stop compatibility monitor"
                    : "Start compatibility monitor");
        }
        if (problem == null && monitorRunning && !PermissionUtils.hasNotificationPermission(this)) {
            statusView.setText("Compatibility monitor running; notifications are disabled");
        } else if (problem == null) {
            String status = "Ready to scan";
            if (monitorRunning) {
                status += "; compatibility monitor running, "
                        + BeaconCache.recentCount(this)
                        + " recent";
            }
            statusView.setText(status);
        } else {
            statusView.setText(problem);
        }
    }

    private void updatePermissionControls() {
        if (permissionButton != null) {
            permissionButton.setVisibility(PermissionUtils.hasScanPermissions(this)
                    ? View.GONE
                    : View.VISIBLE);
        }

        boolean showBackgroundPermission = PermissionUtils.hasScanPermissions(this)
                && PermissionUtils.needsBackgroundLocationPermission()
                && !PermissionUtils.hasBackgroundLocationPermission(this);
        if (backgroundPermissionButton != null) {
            backgroundPermissionButton.setVisibility(showBackgroundPermission
                    ? View.VISIBLE
                    : View.GONE);
        }
        if (backgroundPermissionHint != null) {
            backgroundPermissionHint.setVisibility(showBackgroundPermission
                    ? View.VISIBLE
                    : View.GONE);
        }
    }

    private void showResults(String text) {
        resultsView.setText(text);
        resultsView.setVisibility(View.VISIBLE);
    }

    private ScanConfig readConfigOrShowError() {
        try {
            return ScanConfig.fromInput(
                    uuidField.getText().toString(),
                    majorField.getText().toString(),
                    minorField.getText().toString(),
                    durationField.getText().toString(),
                    minRssiField.getText().toString());
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void fillFromConfig(ScanConfig config) {
        uuidField.setText(config.uuid == null ? "" : config.uuid);
        majorField.setText(config.major == null ? "" : Integer.toString(config.major));
        minorField.setText(config.minor == null ? "" : Integer.toString(config.minor));
        durationField.setText(Integer.toString(config.durationSeconds()));
        minRssiField.setText(config.minRssi == null ? "" : Integer.toString(config.minRssi));
    }

    private EditText textField(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        return editText;
    }

    private void addLabeledField(LinearLayout container, String label, EditText editText) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        container.addView(labelView, matchWrapWithTop(8));
        container.addView(editText, matchWrap());
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
