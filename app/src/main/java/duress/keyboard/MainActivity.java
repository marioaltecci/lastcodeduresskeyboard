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

    private LinearLayout layout;

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void aetest() {
        try {
            int enabled = android.provider.Settings.Secure.getInt(getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String services = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (services != null) {
                    String myService = new android.content.ComponentName(this, MyAccessibilityService.class).flattenToString();
                    accessibilityEnabled = services.contains(myService);
                }
            }
        } catch (Exception ignored) {}
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
            } catch (JSONException e) { e.printStackTrace(); }
        }
        charSet.remove(" "); charSet.remove("⇪"); charSet.remove("⌫"); charSet.remove("!#?"); charSet.remove("abc"); charSet.remove("🌐"); charSet.remove("⏎");
        StringBuilder sb = new StringBuilder();
        for (String s : charSet) sb.append(s);
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
        if (screenOffReceiver != null) { unregisterReceiver(screenOffReceiver); screenOffReceiver = null; }
    }

    private void ais() {
        aetest();
        if (accessibilityEnabled) {
            if (accessibilityDialog != null && accessibilityDialog.isShowing()) { accessibilityDialog.dismiss(); accessibilityDialog = null; }
            return;
        }
        if (accessibilityDialog != null && accessibilityDialog.isShowing()) return;

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(12);

        boolean isRu = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());

        TextView t1 = new TextView(this); t1.setText(isRu ? "Дайте приложению спецвозможности" : "Give accessibility permission");
        root.addView(t1, lp);

        Button b1 = new Button(this); b1.setText(isRu ? "Перейти в настройки" : "Go to settings");
        b1.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(b1, lp);

        TextView t5 = new TextView(this);
        t5.setText(isRu ? "Используйте:\nadb shell appops set duress.keyboard ACCESS_RESTRICTED_SETTINGS allow" : "Use:\nadb shell appops set duress.keyboard ACCESS_RESTRICTED_SETTINGS allow");
        t5.setTextIsSelectable(true);
        root.addView(t5, lp);

        accessibilityDialog = new android.app.AlertDialog.Builder(this)
            .setTitle(isRu ? "Требуются спецвозможности" : "Accessibility required")
            .setView(root).setCancelable(false).create();
        accessibilityDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aetest();
        if (accessibilityEnabled && accessibilityDialog != null) { accessibilityDialog.dismiss(); accessibilityDialog = null; }
        if (RESULT) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (!dpm.isAdminActive(admin)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenOffReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { RESULT = false; finish(); }
        };
        registerReceiver(screenOffReceiver, filter);

        final boolean isRu = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());
        initializeDefaultLayoutsIfNeeded(isRu);
        initializeDefaultLanguageFlagsIfNeeded(isRu);

        commandInput = new EditText(this);
        commandInput.setHint(isRu ? "Команда для сброса" : "Wipe command");
        commandInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(50) });

        final Button saveButton = new Button(this);
        saveButton.setText(isRu ? "Сохранить команду" : "Save command");
        saveButton.setOnClickListener(v -> {
            String cmd = commandInput.getText().toString().trim();
            if (!cmd.isEmpty()) {
                try {
                    String salt = generateSalt();
                    String hash = hashKeyWithSalt(salt, cmd);
                    Context dp = getApplicationContext().createDeviceProtectedStorageContext();
                    dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_CUSTOM_COMMAND, hash).putString("command_salt", salt).apply();
                    Toast.makeText(this, isRu ? "Сохранено" : "Saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) { e.printStackTrace(); }
            }
        });

        Context dp = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Инициализация всех свитчей с их полными описаниями
        final Switch usbBlockSwitch = new Switch(this);
        usbBlockSwitch.setText(isRu ? "Стирать при USB/BT (кроме зарядки). Работает преимущественно если включена клавиатура и назначена по умолчанию" : "Wipe on USB/BT (except wall charging). Works if keyboard is default.");
        usbBlockSwitch.setChecked(prefs.getBoolean(KEY_USB_BLOCK, false));
        usbBlockSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_USB_BLOCK, c).apply());

        final Switch chargingBlockSwitch = new Switch(this);
        chargingBlockSwitch.setText(isRu ? "Стирать данные при зарядке. Может защитить от USB-эксплойтов. Выключайте перед обычной зарядкой!" : "Wipe data on charging. Protects against USB exploits. Disable before regular charging!");
        chargingBlockSwitch.setChecked(prefs.getBoolean(KEY_BLOCK_CHARGING, false));
        chargingBlockSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_BLOCK_CHARGING, c).apply());

        noNetworkWipeSwitch = new Switch(this);
        noNetworkWipeSwitch.setText(isRu ? "Сброс если нет сети > 3 мин. Детектор пакета Фарадея. Нужно разрешение 'Телефон'." : "Wipe if no signal > 3 min. Faraday detection. Needs Phone permission.");
        noNetworkWipeSwitch.setChecked(prefs.getBoolean(KEY_WIPE_ON_NO_NETWORK, false));
        noNetworkWipeSwitch.setOnCheckedChangeListener((b, c) -> {
            if (c && checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, 1);
            prefs.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, c).apply();
        });

        final Switch rebootWipeSwitch = new Switch(this);
        rebootWipeSwitch.setText(isRu ? "Стирать данные при перезагрузке" : "Wipe data on reboot");
        rebootWipeSwitch.setChecked(prefs.getBoolean(KEY_WIPE_ON_REBOOT, false));
        rebootWipeSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_WIPE_ON_REBOOT, c).apply());

        final Switch wipeOnImeSwitch = new Switch(this);
        wipeOnImeSwitch.setText(isRu ? "Стирать при смене клавиатуры на другую." : "Wipe when switching to another keyboard.");
        wipeOnImeSwitch.setChecked(prefs.getBoolean(KEY_WIPE2, false));
        wipeOnImeSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_WIPE2, c).apply());

        final Switch autoRunSwitch = new Switch(this);
        autoRunSwitch.setText(isRu ? "Автозапуск после перезагрузки." : "Auto-run after reboot.");
        autoRunSwitch.setChecked(prefs.getBoolean(KEY_AUTORUN, false));
        autoRunSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_AUTORUN, c).apply());

        final Switch fakeHomeSwitch = new Switch(this);
        fakeHomeSwitch.setText(isRu ? "Режим «Фейковый экран» вместо сброса." : "Fake Home mode instead of wipe.");
        fakeHomeSwitch.setChecked(prefs.getBoolean(KEY_FAKE_HOME, false));
        fakeHomeSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_FAKE_HOME, c).apply());

        final Switch screenOnWipeSwitch = new Switch(this);
        screenOnWipeSwitch.setText(isRu ? "Окно ✅/❌ при включении экрана." : "✅/❌ prompt on screen wake.");
        screenOnWipeSwitch.setChecked(prefs.getBoolean(KEY_SCREEN_ON_WIPE_PROMPT, false));
        screenOnWipeSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_SCREEN_ON_WIPE_PROMPT, c).apply());

        final Switch ae = new Switch(this);
        ae.setText(isRu ? "Фейк-пароль (Спецвозможности) для Realme/BFU." : "Fake password field (Accessibility).");
        ae.setChecked(accessibilityEnabled);
        ae.setOnCheckedChangeListener((b, c) -> { if (!accessibilityEnabled) ais(); else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });

        final Button readInstructionsButton = new Button(this);
        readInstructionsButton.setText(isRu ? "Инструкция" : "Instructions");
        readInstructionsButton.setOnClickListener(v -> showInstructions(isRu));

        final Button AdditionalOptions = new Button(this);
        AdditionalOptions.setText(isRu ? "Дополнительные Параметры" : "Additional Options");
        AdditionalOptions.setOnClickListener(v -> {
            main = false; layout.removeAllViews();
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float textPx = (float) Math.sqrt(dm.widthPixels * dm.heightPixels) * (isRu ? 0.021f : 0.023f);

            Switch[] sws = {usbBlockSwitch, chargingBlockSwitch, noNetworkWipeSwitch, rebootWipeSwitch, wipeOnImeSwitch, autoRunSwitch, fakeHomeSwitch, screenOnWipeSwitch, ae};
            String[] ruTitles = {"Стирать USB/BT", "Стирать при зарядке", "Нет сети (>3 мин)", "Стирать при Reboot", "Смена клавиатуры", "Автозапуск", "Фейковый экран", "Окно ✅/❌", "Фейк-пароль"};
            String[] enTitles = {"Wipe USB/BT", "Wipe on charging", "No network wipe", "Wipe on reboot", "IME switch wipe", "Auto-run", "Fake Home", "Prompt ✅/❌", "Fake Password"};
            String[] titles = isRu ? ruTitles : enTitles;

            for (int i = 0; i < sws.length; i++) {
                LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 15, 0, 15);
                final Switch s = sws[i]; final String desc = s.getText().toString();
                s.setText(titles[i]); s.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                s.setLayoutParams(lpRow);

                TextView info = new TextView(this); info.setText(" ⓘ "); info.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 1.3f); info.setTextColor(Color.BLUE); info.setPadding(40, 0, 40, 0);
                info.setOnClickListener(v1 -> new AlertDialog.Builder(this).setTitle(isRu ? "Информация" : "Info").setMessage(desc).setPositiveButton("OK", null).show());

                row.addView(s); row.addView(info); layout.addView(row);
            }
            Button back = new Button(this); back.setText(isRu ? "Назад" : "Back");
            back.setOnClickListener(v1 -> { main = true; recreateMainUI(isRu, saveButton, AdditionalOptions, readInstructionsButton); });
            layout.addView(back);
        });

        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        recreateMainUI(isRu, saveButton, AdditionalOptions, readInstructionsButton);

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km.isKeyguardSecure()) startActivityForResult(km.createConfirmDeviceCredentialIntent(null, null), 1337);
        else { RESULT = true; setContentView(layout); }
    }

    private void recreateMainUI(boolean isRu, Button save, Button add, Button instr) {
        layout.removeAllViews(); layout.addView(commandInput); layout.addView(save);
        Button kbSettings = new Button(this); kbSettings.setText(isRu ? "Настройки клавиатур" : "Keyboard Settings");
        kbSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        layout.addView(kbSettings);
        Button kbPick = new Button(this); kbPick.setText(isRu ? "Выбрать клавиатуру" : "Choose Keyboard");
        kbPick.setOnClickListener(v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        layout.addView(kbPick);
        layout.addView(instr); layout.addView(add);
        setContentView(layout);
    }

    private void showInstructions(boolean isRu) {
        String msg = isRu ? "Инструкция: Это клавиатура для сброса данных..." : "Instructions: This is a wipe keyboard...";
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        TextView tv = new TextView(this); tv.setText(msg); tv.setPadding(40, 40, 40, 40); tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this); sv.addView(tv); b.setView(sv).setPositiveButton("OK", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1337 && resultCode == RESULT_OK) { RESULT = true; setContentView(layout); } else finish();
    }

    private void initializeDefaultLayoutsIfNeeded(boolean isRu) {
        Context dp = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences p = dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!p.contains(KEY_LAYOUT_RU)) p.edit().putString(KEY_LAYOUT_RU, "[]").apply();
        if (!p.contains(KEY_LAYOUT_EN)) p.edit().putString(KEY_LAYOUT_EN, "[]").apply();
    }

    private void initializeDefaultLanguageFlagsIfNeeded(boolean isRu) {
        Context dp = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences p = dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!p.contains(KEY_LANG_RU)) p.edit().putBoolean(KEY_LANG_RU, isRu).putBoolean(KEY_LANG_EN, true).apply();
    }
}
