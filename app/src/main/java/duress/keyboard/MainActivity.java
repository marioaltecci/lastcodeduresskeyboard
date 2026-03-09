package com.plain.keyboard.soft;

import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.method.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.nio.charset.*;
import java.security.*;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.util.*;
import java.util.regex.*;
import org.json.*;
/*
 Приложение использует DPS, а не Android Keystore,
 потому что на нескоторых устройствах
 даже если setUserAuthenticationRequired(false)), 
 Android Keystore может быть недоступен
 в BFU, а данное приложение являсь клавиатурой
 должно работать в BFU.
 */

/*
 The app uses DPS instead of Android Keystore,
 because on some devices, 
 even if setUserAuthenticationRequired(false), 
 Android Keystore may not be available in BFU, 
 but this app, being a keyboard, should work in BFU.
 */

public class MainActivity extends Activity {

    private android.app.AlertDialog accessibilityDialog;
    private static boolean main = true;
    boolean accessibilityEnabled = false;
    private static final String PREFS_NAME = "SimpleKeyboardPrefs";
    private static final String KEY_CUSTOM_COMMAND = "custom_wipe_command";
    private BroadcastReceiver screenOffReceiver;
    private static final String KEY_WIPE_ON_REBOOT = "wipe_on_reboot";
    private static final String KEY_AUTORUN = "auto_run";
    private static final String KEY_WIPE2 = "wipe2";
    private static final String KEY_SCREEN_ON_WIPE_PROMPT = "screen_on_wipe_prompt";
    private SharedPreferences prefsNetwork;
    private static final String KEY_FAKE_HOME = "fake_home_enabled";
    
    private Switch noNetworkWipeSwitch;
    private static final String KEY_WIPE_ON_NO_NETWORK = "wipe_on_no_network";
    private static final String KEY_USB_BLOCK = "usb_block_enabled";
    private static final String KEY_BLOCK_CHARGING = "block_charging_enabled";
    private static final String KEY_LAYOUT_RU = "layout_ru";
    private static final String KEY_LAYOUT_EN = "layout_en";
    private static final String KEY_LAYOUT_SYM = "layout_sym";
    private static final String KEY_LAYOUT_EMOJI = "layout_emoji";
    private static final String KEY_LAYOUT_ES = "layout_es";
    private static boolean RESULT = false;
    private EditText commandInput; 
    private static final String KEY_LANG_RU = "lang_ru";
    private static final String KEY_LANG_EN = "lang_en";
    private static final String KEY_LANG_SYM = "lang_sym";
    private static final String KEY_LANG_EMOJI = "lang_emoji";
    private static final String KEY_LANG_ES = "lang_es";
    private static int e = 0;
    private boolean isRussianDevice;
    private LinearLayout layout;

    private void aetest() {
        try {
            int enabled = android.provider.Settings.Secure.getInt(
                getContentResolver(),
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (enabled == 1) {
                String services = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

                if (services != null) {
                    String myService = new android.content.ComponentName(
                        this,
                        MyAccessibilityService.class
                    ).flattenToString();

                    accessibilityEnabled = services.contains(myService);
                }
            }
        } catch (Exception ignored) {}
    }

    private int dpToPx(int dp) {    
        float density = getResources().getDisplayMetrics().density;    
        return (int) (dp * density + 0.5f);    
    }  

    private String getAllowedCharacters(Context context) {
        Set<String> charSet = new HashSet<>();
        Context dpContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String[] keys = {KEY_LAYOUT_RU, KEY_LAYOUT_EN, KEY_LAYOUT_ES, KEY_LAYOUT_SYM, KEY_LAYOUT_EMOJI};

        for (String key : keys) {
            String jsonString = prefs.getString(key, "[]");
            try {
                JSONArray outer = new JSONArray(jsonString);
                for (int i = 0; i < outer.length(); i++) {
                    JSONArray inner = outer.getJSONArray(i);
                    for (int j = 0; j < inner.length(); j++) {
                        String symbol = inner.getString(j);

                        if (symbol.length() == 1 || symbol.length() > 1 && Character.isSurrogatePair(symbol.charAt(0), symbol.charAt(1))) {
                            charSet.add(symbol);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        charSet.remove(" "); 
        charSet.remove("⇪"); // Shift
        charSet.remove("⌫"); // Backspace
        charSet.remove("!#?"); // Sym switch
        charSet.remove("abc"); // Alpha switch
        charSet.remove("🌐"); // Lang switch
        charSet.remove("⏎"); // Enter/Wipe trigger

        StringBuilder sb = new StringBuilder();
        for (String s : charSet) {
            sb.append(s);
        }
        return sb.toString();
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashKeyWithSalt(String salt, String cmd) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest((salt + cmd).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RESULT = false;
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
            screenOffReceiver = null;
        }
    }

    private void ais() {
        aetest();

        if (accessibilityEnabled) {
            if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
                accessibilityDialog.dismiss();
                accessibilityDialog = null;
            }
            return;
        }

        if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
            return;
        }

        // ---------- UI ----------
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dpToPx(12);

        TextView t1 = new TextView(this);
        t1.setText(isRussianDevice ? 
            getString(R.string.accessibility_text_1_ru) : 
            getString(R.string.accessibility_text_1_en));
        root.addView(t1, lp);

        TextView t2 = new TextView(this);
        t2.setText(isRussianDevice ? 
            getString(R.string.accessibility_text_2_ru) : 
            getString(R.string.accessibility_text_2_en));
        root.addView(t2, lp);

        Button b1 = new Button(this);
        b1.setText(isRussianDevice ? 
            getString(R.string.accessibility_button_1_ru) : 
            getString(R.string.accessibility_button_1_en));
        root.addView(b1, lp);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override 
            public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        TextView t3 = new TextView(this);
        t3.setText(isRussianDevice ? 
            getString(R.string.accessibility_text_3_ru) : 
            getString(R.string.accessibility_text_3_en));
        root.addView(t3, lp);

        Button b2 = new Button(this);
        b2.setText(isRussianDevice ? 
            getString(R.string.accessibility_button_2_ru) : 
            getString(R.string.accessibility_button_2_en));
        root.addView(b2, lp);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override 
            public void onClick(View v) {
                startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getApplicationContext().getPackageName(), null)
                ));
            }
        });

        TextView t4 = new TextView(this);
        t4.setText(isRussianDevice ? 
            getString(R.string.accessibility_text_4_ru) : 
            getString(R.string.accessibility_text_4_en));
        root.addView(t4, lp);

        Button b3 = new Button(this);
        b3.setText(isRussianDevice ? 
            getString(R.string.accessibility_button_3_ru) : 
            getString(R.string.accessibility_button_3_en));
        root.addView(b3, lp);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override 
            public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        TextView t5 = new TextView(this);
        t5.setText(isRussianDevice ? 
            getString(R.string.accessibility_text_5_ru) : 
            getString(R.string.accessibility_text_5_en));
        t5.setTextIsSelectable(true);
        root.addView(t5, lp);
        
        String title = isRussianDevice ? 
            getString(R.string.title_accessibility_required_ru) : 
            getString(R.string.title_accessibility_required_en);
            
        accessibilityDialog = new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(root)
            .setCancelable(false)
            .create();

        accessibilityDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean accessibilityEnabled = false;

        try {
            int enabled = android.provider.Settings.Secure.getInt(
                getContentResolver(),
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (enabled == 1) {
                String services = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

                if (services != null) {
                    String myService = new android.content.ComponentName(
                        this,
                        MyAccessibilityService.class
                    ).flattenToString();

                    accessibilityEnabled = services.contains(myService);
                }
            }
        } catch (Exception ignored) {}

        if (accessibilityEnabled) {
            if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
                accessibilityDialog.dismiss();
                accessibilityDialog = null;
            }
            return;
        }

        if (RESULT == true) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );

            ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (!dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                String explanation = isRussianDevice ? 
                    getString(R.string.admin_explanation_ru) : 
                    getString(R.string.admin_explanation_en);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation);
                startActivity(intent);
            }
        }
    }

    private void showLanguageSelectionDialog() {
        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final String[] languages = new String[] {
            getString(R.string.lang_russian),
            getString(R.string.lang_english),
            getString(R.string.lang_spanish),
            isRussianDevice ? getString(R.string.lang_symbols_ru) : getString(R.string.lang_symbols_en),
            isRussianDevice ? getString(R.string.lang_emoji_ru) : getString(R.string.lang_emoji_en)
        };

        final String[] keys = {KEY_LANG_RU, KEY_LANG_EN, KEY_LANG_ES, KEY_LANG_SYM, KEY_LANG_EMOJI};
        final boolean[] checkedItems = new boolean[languages.length];

        for (int i = 0; i < keys.length; i++) {
            checkedItems[i] = prefs.getBoolean(keys[i], false);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isRussianDevice ? 
            getString(R.string.title_select_languages_ru) : 
            getString(R.string.title_select_languages_en))
            .setMultiChoiceItems(languages, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    checkedItems[which] = isChecked;
                }
            })
            .setPositiveButton(isRussianDevice ? 
                getString(R.string.action_save_ru) : 
                getString(R.string.action_save_en), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences.Editor ed = prefs.edit();
                    for (int i = 0; i < keys.length; i++) {
                        ed.putBoolean(keys[i], checkedItems[i]);
                    }
                    ed.apply();

                    Toast.makeText(MainActivity.this,
                        isRussianDevice ? 
                            getString(R.string.toast_languages_saved_ru) : 
                            getString(R.string.toast_languages_saved_en),
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(isRussianDevice ? 
                getString(R.string.action_cancel_ru) : 
                getString(R.string.action_cancel_en), null)
            .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    RESULT = false;
                    finish();
                }
            }
        };
        registerReceiver(screenOffReceiver, filter);

        String sysLang = Locale.getDefault().getLanguage();
        isRussianDevice = "ru".equalsIgnoreCase(sysLang);

        initializeDefaultLayoutsIfNeeded(isRussianDevice);
        initializeDefaultLanguageFlagsIfNeeded(isRussianDevice);

        commandInput = new EditText(this);
        commandInput.setHint(isRussianDevice ? 
            getString(R.string.hint_set_command_ru) : 
            getString(R.string.hint_set_command_en));

        final String allowedChars = getAllowedCharacters(this);

        InputFilter filter1 = new InputFilter.LengthFilter(50);

        InputFilter filterChars = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, 
                                       Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (allowedChars.indexOf(source.charAt(i)) == -1) {
                        return "";
                    }
                }
                return null;
            }
        };

        commandInput.setFilters(new InputFilter[] { filter1, filterChars });

        final Button saveButton = new Button(this);
        saveButton.setText(isRussianDevice ? 
            getString(R.string.button_save_command_ru) : 
            getString(R.string.button_save_command_en));

        saveButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                String cmd = commandInput.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    try {
                        String salt = generateSalt();
                        String commandHash = hashKeyWithSalt(salt, cmd);

                        Context deviceProtectedContext = getApplicationContext().createDeviceProtectedStorageContext();
                        SharedPreferences prefs = deviceProtectedContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

                        prefs.edit()
                            .putString(KEY_CUSTOM_COMMAND, commandHash)
                            .putString("command_salt", salt)
                            .apply();

                        String inputHash = "";
                        try {
                            MessageDigest digest = MessageDigest.getInstance("SHA-256");
                            byte[] hashBytes = digest.digest((salt + cmd).getBytes(StandardCharsets.UTF_8));
                            inputHash = Base64.getEncoder().encodeToString(hashBytes);
                        } catch (Exception e) {}

                        if (commandHash.equals(inputHash)) {
                            String message = (isRussianDevice ? 
                                getString(R.string.toast_command_saved_ru) : 
                                getString(R.string.toast_command_saved_en)) + cmd;
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        } 

                        if (!commandHash.equals(inputHash)) {
                            Toast.makeText(MainActivity.this, 
                                isRussianDevice ? 
                                    getString(R.string.toast_hash_error_ru) : 
                                    getString(R.string.toast_hash_error_en),
                                Toast.LENGTH_SHORT).show();                     
                        }

                        commandInput.setText("");
                        commandInput.clearFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);

                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.toast_hash_error_general), 
                            Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        Context dpContextForIme = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsIme = dpContextForIme.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch fakeHomeSwitch = new Switch(MainActivity.this);
        fakeHomeSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_fake_home_ru) : 
            getString(R.string.switch_fake_home_en));

        final Switch screenOnWipeSwitch = new Switch(this);
        screenOnWipeSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_screen_on_wipe_ru) : 
            getString(R.string.switch_screen_on_wipe_en));

        Context dpContextScreen = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsScreen = dpContextScreen.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        screenOnWipeSwitch.setChecked(prefsScreen.getBoolean(KEY_SCREEN_ON_WIPE_PROMPT, false));

        screenOnWipeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsScreen.edit().putBoolean(KEY_SCREEN_ON_WIPE_PROMPT, isChecked).apply();
                Toast.makeText(MainActivity.this, 
                    isRussianDevice ? 
                        (isChecked ? getString(R.string.toast_enabled_ru) : getString(R.string.toast_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_enabled_en) : getString(R.string.toast_disabled_en)), 
                    Toast.LENGTH_SHORT).show();
            }
        });

        boolean savedFakeHomeState = prefsIme.getBoolean(KEY_FAKE_HOME, false);
        fakeHomeSwitch.setChecked(savedFakeHomeState);

        fakeHomeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsIme.edit().putBoolean(KEY_FAKE_HOME, isChecked).apply();
                Toast.makeText(MainActivity.this,
                    isRussianDevice ? 
                        (isChecked ? getString(R.string.toast_enabled_ru) : getString(R.string.toast_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_enabled_en) : getString(R.string.toast_disabled_en)),
                    Toast.LENGTH_SHORT).show();
            }
        });

        final Switch ae = new Switch(this);
        ae.setText(isRussianDevice ? 
            getString(R.string.switch_accessibility_alternative_ru) : 
            getString(R.string.switch_accessibility_alternative_en));

        aetest();
        ae.setChecked(accessibilityEnabled);

        ae.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!accessibilityEnabled) {                
                    aetest();
                    ais(); 
                }

                if (accessibilityEnabled) {
                    aetest();
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    finish();
                }
            }
        });

        final Switch wipeOnImeSwitch = new Switch(this);
        wipeOnImeSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_wipe_on_ime_ru) : 
            getString(R.string.switch_wipe_on_ime_en));

        boolean savedImeWipeState = prefsIme.getBoolean(KEY_WIPE2, false);
        wipeOnImeSwitch.setChecked(savedImeWipeState);

        wipeOnImeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsIme.edit().putBoolean(KEY_WIPE2, isChecked).apply();

                Toast.makeText(MainActivity.this,
                    isRussianDevice ?
                        (isChecked ? getString(R.string.toast_wipe_on_ime_enabled_ru) : 
                                     getString(R.string.toast_wipe_on_ime_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_wipe_on_ime_enabled_en) : 
                                     getString(R.string.toast_wipe_on_ime_disabled_en)),
                    Toast.LENGTH_SHORT).show();
            }
        });

        Context dpContextForReboot = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsReboot = dpContextForReboot.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch rebootWipeSwitch = new Switch(this);
        rebootWipeSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_wipe_on_reboot_ru) : 
            getString(R.string.switch_wipe_on_reboot_en));

        boolean savedRebootWipeState = prefsReboot.getBoolean(KEY_WIPE_ON_REBOOT, false);
        rebootWipeSwitch.setChecked(savedRebootWipeState);

        rebootWipeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsReboot.edit().putBoolean(KEY_WIPE_ON_REBOOT, isChecked).apply();

                Toast.makeText(MainActivity.this,
                    isRussianDevice ?
                        (isChecked ? getString(R.string.toast_wipe_on_reboot_enabled_ru) : 
                                     getString(R.string.toast_wipe_on_reboot_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_wipe_on_reboot_enabled_en) : 
                                     getString(R.string.toast_wipe_on_reboot_disabled_en)),
                    Toast.LENGTH_SHORT).show();
            }
        }); 

        Context dpContextAUTORUN = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsAUTORUN = dpContextAUTORUN.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch AutoRunSwitch = new Switch(this);
        AutoRunSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_autorun_ru) : 
            getString(R.string.switch_autorun_en));

        boolean savedAutoRunState = prefsAUTORUN.getBoolean(KEY_AUTORUN, false);
        AutoRunSwitch.setChecked(savedAutoRunState);

        AutoRunSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsAUTORUN.edit().putBoolean(KEY_AUTORUN, isChecked).apply();

                PackageManager pm = MainActivity.this.getPackageManager();
                ComponentName cn = new ComponentName(MainActivity.this, InputActivity.class);

                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );

                Toast.makeText(MainActivity.this,
                    isRussianDevice ?
                        (isChecked ? getString(R.string.toast_autorun_enabled_ru) : 
                                     getString(R.string.toast_autorun_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_autorun_enabled_en) : 
                                     getString(R.string.toast_autorun_disabled_en)),
                    Toast.LENGTH_SHORT).show();
            }
        }); 

        final Button readInstructionsButton = new Button(this);
        readInstructionsButton.setText(isRussianDevice ? 
            getString(R.string.button_read_instructions_ru) : 
            getString(R.string.button_read_instructions_en));

        readInstructionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String instructions = isRussianDevice ? 
                    getString(R.string.instructions_ru) : 
                    getString(R.string.instructions_en);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                ScrollView scroll = new ScrollView(MainActivity.this);
                int padding = (int) (16 * getResources().getDisplayMetrics().density);

                TextView tv = new TextView(MainActivity.this);
                tv.setText(instructions);
                tv.setTextColor(Color.BLACK);
                tv.setTextSize(16);
                tv.setPadding(padding, padding, padding, padding);
                tv.setTextIsSelectable(true); 

                String text = instructions;

                SpannableString ss = new SpannableString(text);

                Pattern pattern = Pattern.compile("(https?://[A-Za-z0-9/.:\\-_%?=&]+)");
                Matcher matcher = pattern.matcher(text);

                while (matcher.find()) {
                    final String url = matcher.group();

                    ss.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            widget.getContext().startActivity(intent);
                        }

                        @Override
                        public void updateDrawState(TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(Color.BLUE);
                            ds.setUnderlineText(true);
                        }
                    }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                tv.setText(ss);
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                tv.setLinksClickable(true);
                tv.setTextColor(Color.BLACK);
                tv.setTextIsSelectable(true);
                scroll.addView(tv);

                builder.setTitle(isRussianDevice ? 
                    getString(R.string.title_instructions_ru) : 
                    getString(R.string.title_instructions_en));
                builder.setView(scroll);
                builder.setPositiveButton(getString(R.string.action_ok), null);
                builder.show();
            }
        });

        final Button keyboardSettingsButton = new Button(this);
        keyboardSettingsButton.setText(isRussianDevice ? 
            getString(R.string.button_keyboard_settings_ru) : 
            getString(R.string.button_keyboard_settings_en));
        keyboardSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });

        final Button chooseKeyboardButton = new Button(this);
        chooseKeyboardButton.setText(isRussianDevice ? 
            getString(R.string.button_choose_keyboard_ru) : 
            getString(R.string.button_choose_keyboard_en));
        chooseKeyboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                } else {
                    Toast.makeText(MainActivity.this, 
                        isRussianDevice ? 
                            getString(R.string.toast_picker_failed_ru) : 
                            getString(R.string.toast_picker_failed_en), 
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        Context dpContextForUsb = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsUsb = dpContextForUsb.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch usbBlockSwitch = new Switch(this);
        usbBlockSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_usb_block_ru) : 
            getString(R.string.switch_usb_block_en));

        boolean savedUsbBlockState = prefsUsb.getBoolean(KEY_USB_BLOCK, false);
        usbBlockSwitch.setChecked(savedUsbBlockState);

        usbBlockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefsUsb.edit().putBoolean(KEY_USB_BLOCK, isChecked).apply();

                Toast.makeText(MainActivity.this,
                    isRussianDevice ?
                        (isChecked ? getString(R.string.toast_usb_block_enabled_ru) : 
                                     getString(R.string.toast_usb_block_disabled_ru)) :
                        (isChecked ? getString(R.string.toast_usb_block_enabled_en) : 
                                     getString(R.string.toast_usb_block_disabled_en)),
                    Toast.LENGTH_SHORT).show();
            }
        });

        final Button selectLanguagesButton = new Button(this);
        selectLanguagesButton.setText(isRussianDevice ? 
            getString(R.string.button_select_languages_ru) : 
            getString(R.string.button_select_languages_en));
        selectLanguagesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageSelectionDialog();
            }
        });

        final Switch chargingBlockSwitch = new Switch(this);
        chargingBlockSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_charging_block_ru) : 
            getString(R.string.switch_charging_block_en));

        boolean savedChargingBlockState = prefsUsb.getBoolean(KEY_BLOCK_CHARGING, false);
        chargingBlockSwitch.setChecked(savedChargingBlockState);

        chargingBlockSwitch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    final boolean currentState = chargingBlockSwitch.isChecked();

                    if (!currentState) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(isRussianDevice ? 
                                getString(R.string.title_confirmation_ru) : 
                                getString(R.string.title_confirmation_en))
                            .setMessage(isRussianDevice ?
                                getString(R.string.confirmation_charging_ru) :
                                getString(R.string.confirmation_charging_en))
                            .setPositiveButton(getString(R.string.action_ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    chargingBlockSwitch.setChecked(true); 
                                    prefsUsb.edit().putBoolean(KEY_BLOCK_CHARGING, true).apply();
                                    Toast.makeText(MainActivity.this,
                                        isRussianDevice ? 
                                            getString(R.string.toast_charging_block_enabled_ru) : 
                                            getString(R.string.toast_charging_block_enabled_en),
                                        Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(isRussianDevice ? 
                                getString(R.string.action_cancel_ru) : 
                                getString(R.string.action_cancel_en), null)
                            .show();
                    } else { 
                        chargingBlockSwitch.setChecked(false); 
                        prefsUsb.edit().putBoolean(KEY_BLOCK_CHARGING, false).apply();
                        Toast.makeText(MainActivity.this,
                            isRussianDevice ? 
                                getString(R.string.toast_charging_block_disabled_ru) : 
                                getString(R.string.toast_charging_block_disabled_en),
                            Toast.LENGTH_SHORT).show();
                    }
                }
                return true; 
            }
        });

        noNetworkWipeSwitch = new Switch(this);
        noNetworkWipeSwitch.setText(isRussianDevice ? 
            getString(R.string.switch_network_wipe_ru) : 
            getString(R.string.switch_network_wipe_en));

        Context dpContextForNetwork = getApplicationContext().createDeviceProtectedStorageContext();
        prefsNetwork = dpContextForNetwork.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        boolean savedNoNetworkWipeState = prefsNetwork.getBoolean(KEY_WIPE_ON_NO_NETWORK, false);
        noNetworkWipeSwitch.setChecked(savedNoNetworkWipeState);

        noNetworkWipeSwitch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    final boolean willEnable = !noNetworkWipeSwitch.isChecked();

                    if (willEnable) {
                        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                            != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{ android.Manifest.permission.READ_PHONE_STATE }, 1);
                        } else {
                            noNetworkWipeSwitch.setChecked(true);
                            prefsNetwork.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, true).apply();
                            Toast.makeText(MainActivity.this,
                                isRussianDevice ? 
                                    getString(R.string.toast_network_wipe_enabled_ru) :
                                    getString(R.string.toast_network_wipe_enabled_en),
                                Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        noNetworkWipeSwitch.setChecked(false);
                        prefsNetwork.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, false).apply();
                        Toast.makeText(MainActivity.this,
                            isRussianDevice ? 
                                getString(R.string.toast_network_wipe_disabled_ru) :
                                getString(R.string.toast_network_wipe_disabled_en),
                            Toast.LENGTH_SHORT).show();
                    }
                }
                return true; 
            }
        });

        final Button AdditionalOptions = new Button(this);
        AdditionalOptions.setText(isRussianDevice ? 
            getString(R.string.button_additional_options_ru) : 
            getString(R.string.button_additional_options_en));   
        AdditionalOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main = false;
                layout.removeAllViews(); 
                DisplayMetrics dm = getResources().getDisplayMetrics();

                float textPx = (float) Math.sqrt(dm.widthPixels * dm.heightPixels) * 0.023f;

                if (isRussianDevice) {
                    textPx = (float) Math.sqrt(dm.widthPixels * dm.heightPixels) * 0.021f;
                }

                usbBlockSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                chargingBlockSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                noNetworkWipeSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                rebootWipeSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                wipeOnImeSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                AutoRunSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                fakeHomeSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                screenOnWipeSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                ae.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                layout.addView(usbBlockSwitch);
                layout.addView(chargingBlockSwitch);
                layout.addView(noNetworkWipeSwitch); 
                layout.addView(rebootWipeSwitch);
                layout.addView(wipeOnImeSwitch);
                layout.addView(AutoRunSwitch);
                layout.addView(fakeHomeSwitch);
                layout.addView(screenOnWipeSwitch);
                layout.addView(ae);

                final Button AdditionalOptionsBack = new Button(MainActivity.this);
                AdditionalOptionsBack.setText(isRussianDevice ? 
                    getString(R.string.button_main_menu_ru) : 
                    getString(R.string.button_main_menu_en));   
                AdditionalOptionsBack.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        main = true;
                        layout.removeAllViews(); 
                        layout.setOrientation(LinearLayout.VERTICAL);
                        
                        // ВОТ ЗДЕСЬ НОВЫЙ ДИЗАЙН ГЛАВНОГО ЭКРАНА
                        addMainScreenViews(layout, commandInput, saveButton, 
                            keyboardSettingsButton, chooseKeyboardButton, 
                            selectLanguagesButton, readInstructionsButton, AdditionalOptions);
                    }
                });
                layout.addView(AdditionalOptionsBack);
                setContentView(layout);
            }
        });

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        // ВОТ ЗДЕСЬ НОВЫЙ ДИЗАЙН ГЛАВНОГО ЭКРАНА
        addMainScreenViews(layout, commandInput, saveButton, 
            keyboardSettingsButton, chooseKeyboardButton, 
            selectLanguagesButton, readInstructionsButton, AdditionalOptions);

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        if (keyguardManager.isKeyguardSecure()) {
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
            if (intent != null) {
                startActivityForResult(intent, 1337);
            }
        } else { 
            RESULT = true;
            setContentView(layout);
        }
    }

    // НОВЫЙ МЕТОД ДЛЯ СОЗДАНИЯ ГЛАВНОГО ЭКРАНА
    private void addMainScreenViews(LinearLayout layout, EditText commandInput, 
                                    Button saveButton, Button keyboardSettingsButton,
                                    Button chooseKeyboardButton, Button selectLanguagesButton,
                                    Button readInstructionsButton, Button additionalOptions) {
        
        // Заголовок раздела сброса
        TextView wipeSection = new TextView(this);
        if (isRussianDevice) {
            wipeSection.setText("⚙️ НАСТРОЙКА СБРОСА ДАННЫХ");
        } else {
            wipeSection.setText("⚙️ WIPE DATA SETUP");
        }
        wipeSection.setTextSize(18);
        wipeSection.setTypeface(null, Typeface.BOLD);
        wipeSection.setTextColor(Color.parseColor("#333333"));
        wipeSection.setPadding(0, 16, 0, 8);
        layout.addView(wipeSection);

        // Поле ввода
        if (isRussianDevice) {
            commandInput.setHint("Введите секретный код");
        } else {
            commandInput.setHint("Enter secret code");
        }
        layout.addView(commandInput);

        // Кнопка сохранения
        if (isRussianDevice) {
            saveButton.setText("💾 Сохранить код");
        } else {
            saveButton.setText("💾 Save code");
        }
        layout.addView(saveButton);

        // Заголовок раздела клавиатуры
        TextView keyboardSection = new TextView(this);
        if (isRussianDevice) {
            keyboardSection.setText("⌨️ КЛАВИАТУРА");
        } else {
            keyboardSection.setText("⌨️ KEYBOARD");
        }
        keyboardSection.setTextSize(18);
        keyboardSection.setTypeface(null, Typeface.BOLD);
        keyboardSection.setTextColor(Color.parseColor("#333333"));
        keyboardSection.setPadding(0, 24, 0, 8);
        layout.addView(keyboardSection);

        // Кнопки клавиатуры
        if (isRussianDevice) {
            keyboardSettingsButton.setText("└── Включить в системе");
            chooseKeyboardButton.setText("└── Сделать основной");
            selectLanguagesButton.setText("└── Выбрать язык");
        } else {
            keyboardSettingsButton.setText("└── Enable in system");
            chooseKeyboardButton.setText("└── Set as default");
            selectLanguagesButton.setText("└── Select language");
        }
        layout.addView(keyboardSettingsButton);
        layout.addView(chooseKeyboardButton);
        layout.addView(selectLanguagesButton);

        // Заголовок раздела инструкции
        TextView instructionsSection = new TextView(this);
        if (isRussianDevice) {
            instructionsSection.setText("📖 ИНСТРУКЦИЯ");
        } else {
            instructionsSection.setText("📖 INSTRUCTIONS");
        }
        instructionsSection.setTextSize(18);
        instructionsSection.setTypeface(null, Typeface.BOLD);
        instructionsSection.setTextColor(Color.parseColor("#333333"));
        instructionsSection.setPadding(0, 24, 0, 8);
        layout.addView(instructionsSection);

        // Кнопка инструкции
        if (isRussianDevice) {
            readInstructionsButton.setText("└── Как это работает");
        } else {
            readInstructionsButton.setText("└── How it works");
        }
        layout.addView(readInstructionsButton);

        // Заголовок раздела дополнительно
        TextView advancedSection = new TextView(this);
        if (isRussianDevice) {
            advancedSection.setText("⚡ ДОПОЛНИТЕЛЬНО");
        } else {
            advancedSection.setText("⚡ ADVANCED");
        }
        advancedSection.setTextSize(18);
        advancedSection.setTypeface(null, Typeface.BOLD);
        advancedSection.setTextColor(Color.parseColor("#333333"));
        advancedSection.setPadding(0, 24, 0, 8);
        layout.addView(advancedSection);

        // Кнопка дополнительных опций
        if (isRussianDevice) {
            additionalOptions.setText("└── Скрытые настройки");
        } else {
            additionalOptions.setText("└── Hidden settings");
        }
        layout.addView(additionalOptions);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1337) {
            if (resultCode == RESULT_OK) {          
                RESULT = true;
                setContentView(layout);
            } else {
                finish();
            }
        }
    }

    private void initializeDefaultLayoutsIfNeeded(boolean isRussianDevice) {
        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_LAYOUT_RU)) {
            String[][] russianLetters = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"й","ц","у","к","е","н","г","ш","щ","з","х"},
                {"ф","ы","в","а","п","р","о","л","д","ж","э"},
                {"⇪","я","ч","с","м","и","т","ь","б","ю","⌫"},
                {"!#?","🌐",","," ",".","⏎"}
            };
            ed.putString(KEY_LAYOUT_RU, string2DArrayToJson(russianLetters));
            changed = true;
        }
        if (!prefs.contains(KEY_LAYOUT_EN)) {
            String[][] englishLetters = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"q","w","e","r","t","y","u","i","o","p"},
                {"a","s","d","f","g","h","j","k","l"},
                {"⇪","z","x","c","v","b","n","m","⌫"},
                {"!#?","🌐",","," ",".","⏎"}
            };
            ed.putString(KEY_LAYOUT_EN, string2DArrayToJson(englishLetters));
            changed = true;
        }
        if (!prefs.contains(KEY_LAYOUT_SYM)) {
            String[][] symbolLetters = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"/","\\","`","+","*","@","#","$","^","&","'"},
                {"=","|","<",">","[","]","(",")","{","}","\""},
                {"😃","~","%","-","—","_",":",";","!","?","⌫"},
                {"abc","🌐",","," ",".","⏎"}
            };
            ed.putString(KEY_LAYOUT_SYM, string2DArrayToJson(symbolLetters));
            changed = true;
        }
        if (!prefs.contains(KEY_LAYOUT_EMOJI)) {
            String[][] emojiLetters = {
                {"😀","😢","😡","🤡","💩","👍","😭","🤬","😵","☠️","😄"},
                {"😁","😔","😤","😜","🤢","😆","😟","😠","😝","🤮","👎"},
                {"😂","😞","😣","😛","😷","🤣","🥰","😖","🤨","🤒","🤧"},
                {"!#?","😊","😫","🧐","🥴","💔","☹️","😩","🐷","😵‍💫","⌫"},
                {"abc","🌐",","," ",".","⏎"}
            };
            ed.putString(KEY_LAYOUT_EMOJI, string2DArrayToJson(emojiLetters));
            changed = true;
        }
        if (!prefs.contains(KEY_LAYOUT_ES)) {
            String[][] spanishLetters = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"q","w","e","r","t","y","u","i","o","p"},
                {"a","s","d","f","g","h","j","k","l","ñ"},
                {"⇪","z","x","c","v","b","n","m","⌫"},
                {"!#?","🌐",","," ",".","⏎"}
            };
            ed.putString(KEY_LAYOUT_ES, string2DArrayToJson(spanishLetters));
            changed = true;
        }
        if (changed) ed.apply();
    }

    private void initializeDefaultLanguageFlagsIfNeeded(boolean isRussianDevice) {
        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_LANG_RU) && !prefs.contains(KEY_LANG_EN) && !prefs.contains(KEY_LANG_ES)
            && !prefs.contains(KEY_LANG_SYM) && !prefs.contains(KEY_LANG_EMOJI)) {
            if (isRussianDevice) {
                ed.putBoolean(KEY_LANG_RU, true);
                ed.putBoolean(KEY_LANG_EN, true);
                ed.putBoolean(KEY_LANG_ES, false);
                ed.putBoolean(KEY_LANG_SYM, true);
                ed.putBoolean(KEY_LANG_EMOJI, true);
            } else {
                ed.putBoolean(KEY_LANG_RU, false);
                ed.putBoolean(KEY_LANG_EN, true);
                ed.putBoolean(KEY_LANG_ES, true);
                ed.putBoolean(KEY_LANG_SYM, true);
                ed.putBoolean(KEY_LANG_EMOJI, true);
            }
            changed = true;
        }
        if (changed) ed.apply();
    }

    private String string2DArrayToJson(String[][] arr) {
        JSONArray outer = new JSONArray();
        for (int i = 0; i < arr.length; i++) {
            JSONArray inner = new JSONArray();
            for (int j = 0; j < arr[i].length; j++) {
                inner.put(arr[i][j]);
            }
            outer.put(inner);
        }
        return outer.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                noNetworkWipeSwitch.setChecked(true);
                prefsNetwork.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, true).apply();
            } else {
                noNetworkWipeSwitch.setChecked(false);
            }
        }
    }

    public static String getCustomCommand(Context context) {
        Context deviceProtectedContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences prefs = deviceProtectedContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_COMMAND, "");
    }
			}
