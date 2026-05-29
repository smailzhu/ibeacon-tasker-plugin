package io.github.smailzhu.ibeacontasker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ScanConfigActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 101;
    private static final int REQUEST_BACKGROUND_LOCATION = 102;

    private EditText uuidField;
    private EditText majorField;
    private EditText minorField;
    private EditText durationField;
    private EditText minRssiField;
    private TextView statusView;
    private Button permissionButton;
    private Button backgroundPermissionButton;
    private TextView backgroundPermissionHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        fillFromConfig(ScanConfig.fromBundle(getIntent().getBundleExtra(LocaleConstants.EXTRA_BUNDLE)));
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS || requestCode == REQUEST_BACKGROUND_LOCATION) {
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
        title.setText("Scan iBeacon");
        title.setTextSize(22);
        container.addView(title, matchWrap());

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

        permissionButton = new Button(this);
        permissionButton.setText("Grant permissions");
        permissionButton.setOnClickListener(v -> requestPermissions(
                PermissionUtils.appRuntimePermissions(),
                REQUEST_PERMISSIONS));
        container.addView(permissionButton, matchWrapWithTop(12));

        if (PermissionUtils.needsBackgroundLocationPermission()) {
            backgroundPermissionButton = new Button(this);
            backgroundPermissionButton.setText("Background location settings");
            backgroundPermissionButton.setOnClickListener(v -> requestBackgroundLocation());
            container.addView(backgroundPermissionButton, matchWrapWithTop(8));

            backgroundPermissionHint = new TextView(this);
            backgroundPermissionHint.setText(PermissionUtils.backgroundLocationSettingsHint(this));
            backgroundPermissionHint.setTextSize(12);
            container.addView(backgroundPermissionHint, matchWrapWithTop(4));
        }

        Button saveButton = new Button(this);
        saveButton.setText("Save");
        saveButton.setOnClickListener(v -> save());
        container.addView(saveButton, matchWrapWithTop(8));

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        container.addView(cancelButton, matchWrapWithTop(8));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(16), 0, 0);
        container.addView(statusView, matchWrap());
        return scrollView;
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

    private void save() {
        ScanConfig config;
        try {
            config = ScanConfig.fromInput(
                    uuidField.getText().toString(),
                    majorField.getText().toString(),
                    minorField.getText().toString(),
                    durationField.getText().toString(),
                    minRssiField.getText().toString());
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent result = new Intent();
        result.putExtra(LocaleConstants.EXTRA_BUNDLE, config.toBundle());
        result.putExtra(LocaleConstants.EXTRA_STRING_BLURB, config.toBlurb());

        Bundle hostExtras = getIntent() == null ? null : getIntent().getExtras();
        if (TaskerCompat.Setting.hostSupportsSynchronousExecution(hostExtras)) {
            TaskerCompat.Setting.requestTimeoutMS(result, config.durationMs + 15000);
        }
        if (TaskerCompat.hostSupportsRelevantVariables(hostExtras)) {
            TaskerCompat.addRelevantVariableList(result, TaskerVariables.RELEVANT_VARIABLES);
        }

        setResult(RESULT_OK, result);
        finish();
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        updatePermissionControls();
        String problem = PermissionUtils.taskerScanReadinessProblem(this);
        statusView.setText(problem == null ? "Ready to scan from Tasker" : problem);
    }

    private void updatePermissionControls() {
        if (permissionButton != null) {
            permissionButton.setVisibility(PermissionUtils.hasScanPermissions(this)
                    ? View.GONE
                    : View.VISIBLE);
        }

        boolean showBackgroundPermission = PermissionUtils.needsBackgroundLocationPermission()
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
