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

    private LinearLayout layout;

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
            } catch (JSONException e) { e.printStackTrace(); }
        }
        charSet.remove(" ");
        charSet.remove("⇪");
        charSet.remove("⌫");
        charSet.remove("!#?");
        charSet.remove("abc");
        charSet.remove("🌐");
        charSet.remove("⏎");
        StringBuilder sb = new StringBuilder();
        for (String s : charSet) { sb.append(s); }
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
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dpToPx(12);

        TextView t1 = new TextView(this);
        t1.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Дайте приложению спецвозможности" : "Give accessibility permission to the app");
        root.addView(t1, lp);

        TextView t2 = new TextView(this);
        t2.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Перейдите в настройки спецвозможностей и там включите их для нашего приложения." : "Go to accessibility settings and enable them for our app.");
        root.addView(t2, lp);

        Button b1 = new Button(this);
        b1.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Перейти в настроки спецвозможностей" : "Go to accessibility settings");
        root.addView(b1, lp);
        b1.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        TextView t3 = new TextView(this);
        t3.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Если вам в настройках спецвозможностей сказали, что это ограниченная настройка, то перейдите в настройки приложения, нажмите три точки в правом верхнем углу и затем нажмите разрешить ограниченные настройки." : "If you're told in Accessibility settings that this is a restricted setting, go to the app settings, tap the three dots in the upper right corner, and then tap Allow restricted settings.");
        root.addView(t3, lp);

        Button b2 = new Button(this);
        b2.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Перейти в настройки приложения" : "Go to the app settings");
        root.addView(b2, lp);
        b2.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", getPackageName(), null))));

        TextView t4 = new TextView(this);
        t4.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Затем снова перейдите в настройки спецвозможностей и включите их для нашего приложения." : "Then go back to the accessibility settings and enable them for our app.");
        root.addView(t4, lp);

        Button b3 = new Button(this);
        b3.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Перейти в настройки спецвозможностей" : "Go to the accessibility settings");
        root.addView(b3, lp);
        b3.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        TextView t5 = new TextView(this);
        t5.setText("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Не помогло? Переустановите приложение.\nНе помогло? Перезагрузите телефон.\nНе помогло? Используйте:\n\nadb shell appops set duress.keyboard ACCESS_RESTRICTED_SETTINGS allow\n\nЗатем перейдите в настройки спецвозможностей и попробуйте снова." : "Don't help? Reinstall app.\nDon't help? Reboot the phone.\nDon't help? Use:\n\nadb shell appops set duress.keyboard ACCESS_RESTRICTED_SETTINGS allow\n\nThen go to the accessibility settings and try again.");
        t5.setTextIsSelectable(true);
        root.addView(t5, lp);

        accessibilityDialog = new android.app.AlertDialog.Builder(this)
            .setTitle("ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Требуются спецвозможности" : "Accessibility required")
            .setView(root).setCancelable(false).create();
        accessibilityDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aetest();
        if (accessibilityEnabled) {
            if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
                accessibilityDialog.dismiss();
                accessibilityDialog = null;
            }
            return;
        }
        if (RESULT == true) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (!dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                String explanation = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? "Дайте разрешение Администратора. Необходимо для работы функции стирания данных." : "Grant Administrator permission. This is required for the data wipe feature to work.";
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation);
                startActivity(intent);
            }
        }
    }

    private void showLanguageSelectionDialog() {
        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean isRussianDevice = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());
        final String[] languages = new String[] { "Русский (Russian)", "English (English)", "Español (Spanish)", isRussianDevice ? "Символы (!#?)" : "Symbols (!#?)", isRussianDevice ? "Эмодзи (😡🤡👍)" : "Emoji (😡🤡👍)" };
        final String[] keys = {KEY_LANG_RU, KEY_LANG_EN, KEY_LANG_ES, KEY_LANG_SYM, KEY_LANG_EMOJI};
        final boolean[] checkedItems = new boolean[languages.length];
        for (int i = 0; i < keys.length; i++) { checkedItems[i] = prefs.getBoolean(keys[i], false); }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isRussianDevice ? "Выберите языки клавиатуры" : "Select keyboard languages")
            .setMultiChoiceItems(languages, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton(isRussianDevice ? "Сохранить" : "Save", (dialog, which) -> {
                SharedPreferences.Editor ed = prefs.edit();
                for (int i = 0; i < keys.length; i++) { ed.putBoolean(keys[i], checkedItems[i]); }
                ed.apply();
                Toast.makeText(MainActivity.this, isRussianDevice ? "Языки сохранены" : "Languages saved", Toast.LENGTH_SHORT).show();
            }).setNegativeButton(isRussianDevice ? "Отмена" : "Cancel", null).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { RESULT = false; finish(); }
            }
        };
        registerReceiver(screenOffReceiver, filter);

        final boolean isRussianDevice = "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());
        initializeDefaultLayoutsIfNeeded(isRussianDevice);
        initializeDefaultLanguageFlagsIfNeeded(isRussianDevice);

        commandInput = new EditText(this);
        commandInput.setHint(isRussianDevice ? "Задайте команду для сброса данных" : "Set wipe data command");
        commandInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(50), (source, start, end, dest, dstart, dend) -> {
            String allowed = getAllowedCharacters(MainActivity.this);
            for (int i = start; i < end; i++) { if (allowed.indexOf(source.charAt(i)) == -1) return ""; }
            return null;
        }});

        final Button saveButton = new Button(this);
        saveButton.setText(isRussianDevice ? "Сохранить команду" : "Save command");
        saveButton.setOnClickListener(v -> {
            String cmd = commandInput.getText().toString().trim();
            if (!cmd.isEmpty()) {
                try {
                    String salt = generateSalt();
                    String commandHash = hashKeyWithSalt(salt, cmd);
                    Context dp = getApplicationContext().createDeviceProtectedStorageContext();
                    dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_CUSTOM_COMMAND, commandHash).putString("command_salt", salt).apply();
                    Toast.makeText(MainActivity.this, (isRussianDevice ? "Сохранено: " : "Saved: ") + cmd, Toast.LENGTH_SHORT).show();
                    commandInput.setText("");
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
                } catch (Exception e) { e.printStackTrace(); }
            }
        });

        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch fakeHomeSwitch = new Switch(this);
        fakeHomeSwitch.setText(isRussianDevice ? "Вместо сброса данных при вводе кода сброса запускать фейковый домашний экран. Даже если не включено, эта опция будет автоиспользована если сброс данных не сработает. Если вы включаете это, вы просто отключаете сброс данных." : "Instead of resetting data, launch a fake home screen. Autoused if wipe fails. Enabling this disables real wipe.");
        fakeHomeSwitch.setChecked(prefs.getBoolean(KEY_FAKE_HOME, false));
        fakeHomeSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_FAKE_HOME, checked).apply());

        final Switch screenOnWipeSwitch = new Switch(this);
        screenOnWipeSwitch.setText(isRussianDevice ? "При каждом включении экрана запускать окно с кнопками ✅, ❌. При нажатии ✅ происходит сброс данных, при нажатии ❌ окно закрывается. Работает только если клавиатура включена и назначена по умолчанию." : "Every screen on, show ✅/❌ window. ✅ wipes data, ❌ closes it. Requires keyboard as default.");
        screenOnWipeSwitch.setChecked(prefs.getBoolean(KEY_SCREEN_ON_WIPE_PROMPT, false));
        screenOnWipeSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_SCREEN_ON_WIPE_PROMPT, checked).apply());

        final Switch ae = new Switch(this);
        ae.setText(isRussianDevice ? "Запускать фейковое поле ввода пароля при каждом включении экрана / перезагрузке в BFU, чтобы в случае чего вы могли ввести туда код сброса данных. Для запуска используется сервис спецвозможностей вместо клавиатуры. Включайте это как альтернативу клавиатуре, если она не работает у вас на экране блокировки (что бывает на некоторых китайских телефонах, например Realme)." : "Launch fake password field on screen on/BFU reboot via Accessibility. Use if keyboard is hidden on lockscreen (e.g. Realme).");
        aetest(); ae.setChecked(accessibilityEnabled);
        ae.setOnCheckedChangeListener((b, checked) -> { if (!accessibilityEnabled) { aetest(); ais(); } else { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); finish(); } });

        final Switch wipeOnImeSwitch = new Switch(this);
        wipeOnImeSwitch.setText(isRussianDevice ? "Стирать данные при переключении на другую виртуальную клавиатуру. Работает только если перед этим данная клавиатура была включена и назначена по умолчанию. Может не работать в безопасном режиме, поэтому лучше просто отключать другие клавиатуры." : "Wipe data when switching to another keyboard. Works if this was default. May fail in Safe Mode.");
        wipeOnImeSwitch.setChecked(prefs.getBoolean(KEY_WIPE2, false));
        wipeOnImeSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_WIPE2, checked).apply());

        final Switch rebootWipeSwitch = new Switch(this);
        rebootWipeSwitch.setText(isRussianDevice ? "Стирать данные при перезагрузке" : "Wipe data on reboot");
        rebootWipeSwitch.setChecked(prefs.getBoolean(KEY_WIPE_ON_REBOOT, false));
        rebootWipeSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_WIPE_ON_REBOOT, checked).apply());

        final Switch AutoRunSwitch = new Switch(this);
        AutoRunSwitch.setText(isRussianDevice ? "Автозапуск экрана с полем ввода после перезагрузки (для запуска клавиатуры, чтобы сразу начать реагировать на тригеры). Рекомендуется включить эту опцию. Может однократно блокировать экран после перезагрузки чтобы избежать ошибок наложения на экран блокировки." : "Auto-launch input screen after reboot to wake keyboard triggers. Recommended. May lock screen once.");
        AutoRunSwitch.setChecked(prefs.getBoolean(KEY_AUTORUN, false));
        AutoRunSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_AUTORUN, checked).apply();
            getPackageManager().setComponentEnabledSetting(new ComponentName(this, InputActivity.class), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        });

        final Button readInstructionsButton = new Button(this);
        readInstructionsButton.setText(isRussianDevice ? "Инструкция" : "Instructions");
        readInstructionsButton.setOnClickListener(v -> showInstructions(isRussianDevice));

        final Button keyboardSettingsButton = new Button(this);
        keyboardSettingsButton.setText(isRussianDevice ? "Настройки клавиатур" : "Keyboard settings");
        keyboardSettingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        final Button chooseKeyboardButton = new Button(this);
        chooseKeyboardButton.setText(isRussianDevice ? "Выбрать клавиатуру" : "Choose keyboard");
        chooseKeyboardButton.setOnClickListener(v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());

        final Switch usbBlockSwitch = new Switch(this);
        usbBlockSwitch.setText(isRussianDevice ? "Стирать данные при обнаружении любых внешних (даже Bluetooth) input methods и USB-подключений, за исключением зарядки от обычного зарядного блока. Работает преимущественно если включена клавиатура и назначена по умолчанию" : "Wipe on external input/USB (except wall charging). Works best if keyboard is default.");
        usbBlockSwitch.setChecked(prefs.getBoolean(KEY_USB_BLOCK, false));
        usbBlockSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(KEY_USB_BLOCK, checked).apply());

        final Switch chargingBlockSwitch = new Switch(this);
        chargingBlockSwitch.setText(isRussianDevice ? "Стирать данные при зарядке. Работает преимущественно если включена клавиатура и назначена по умолчанию. Теоретически, может защитить от сложных USB-exploits." : "Wipe data on charging. Protection against USB exploits.");
        chargingBlockSwitch.setChecked(prefs.getBoolean(KEY_BLOCK_CHARGING, false));
        chargingBlockSwitch.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!chargingBlockSwitch.isChecked()) {
                    new AlertDialog.Builder(MainActivity.this).setTitle(isRussianDevice ? "Внимание" : "Warning").setMessage(isRussianDevice ? "Стереть данные при зарядке прямо сейчас?" : "Wipe data if charging right now?")
                        .setPositiveButton("OK", (d, w) -> { chargingBlockSwitch.setChecked(true); prefs.edit().putBoolean(KEY_BLOCK_CHARGING, true).apply(); }).setNegativeButton(isRussianDevice ? "Отмена" : "Cancel", null).show();
                } else { chargingBlockSwitch.setChecked(false); prefs.edit().putBoolean(KEY_BLOCK_CHARGING, false).apply(); }
            } return true;
        });

        noNetworkWipeSwitch = new Switch(this);
        noNetworkWipeSwitch.setText(isRussianDevice ? "Сброс если нет мобильной сети больше 3 минут. Детектор пакета Фарадея. Требует разрешение 'Телефон'." : "Wipe if no signal for 3 min. Faraday detection. Needs Phone permission.");
        noNetworkWipeSwitch.setChecked(prefs.getBoolean(KEY_WIPE_ON_NO_NETWORK, false));
        noNetworkWipeSwitch.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!noNetworkWipeSwitch.isChecked()) {
                    if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, 1);
                    else { noNetworkWipeSwitch.setChecked(true); prefs.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, true).apply(); }
                } else { noNetworkWipeSwitch.setChecked(false); prefs.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, false).apply(); }
            } return true;
        });

        final Button AdditionalOptions = new Button(this);
        AdditionalOptions.setText(isRussianDevice ? "Дополнительные Параметры" : "Additional Options");
        AdditionalOptions.setOnClickListener(v -> {
            main = false; layout.removeAllViews();
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float textPx = (float) Math.sqrt(dm.widthPixels * dm.heightPixels) * (isRussianDevice ? 0.021f : 0.023f);

            Switch[] switches = {usbBlockSwitch, chargingBlockSwitch, noNetworkWipeSwitch, rebootWipeSwitch, wipeOnImeSwitch, AutoRunSwitch, fakeHomeSwitch, screenOnWipeSwitch, ae};
            String[] ruTitles = {"Стирать при USB/BT (кроме зарядки)", "Стирать при зарядке", "Сброс при потере сети (>3 мин)", "Стирать при перезагрузке", "Стирать при смене клавиатуры", "Автозапуск после загрузки", "Режим «Фейковый экран»", "Окно ✅/❌ при вкл. экрана", "Фейк-пароль (Спецвозможности)"};
            String[] enTitles = {"Wipe on USB/BT (no charging)", "Wipe on charging", "Wipe on network loss (>3 min)", "Wipe on reboot", "Wipe on IME switch", "Auto-run after reboot", "Fake Home mode", "Prompt ✅/❌ on screen wake", "Fake password (Accessibility)"};
            String[] currentTitles = isRussianDevice ? ruTitles : enTitles;

            for (int i = 0; i < switches.length; i++) {
                final Switch s = switches[i];
                final String desc = s.getText().toString();
                s.setText(currentTitles[i]);
                s.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
                s.setMaxLines(1); s.setEllipsize(TextUtils.TruncateAt.END);
                s.setOnClickListener(v1 -> new AlertDialog.Builder(MainActivity.this).setTitle(isRussianDevice ? "Детали" : "Details").setMessage(desc).setPositiveButton("OK", null).show());
                layout.addView(s);
            }

            Button back = new Button(MainActivity.this);
            back.setText(isRussianDevice ? "Назад" : "Back");
            back.setOnClickListener(v1 -> { main = true; layout.removeAllViews(); layout.addView(commandInput); layout.addView(saveButton); layout.addView(keyboardSettingsButton); layout.addView(chooseKeyboardButton); layout.addView(new Button(MainActivity.this) {{ setText(isRussianDevice ? "Выбрать языки" : "Select languages"); setOnClickListener(v2 -> showLanguageSelectionDialog()); }}); layout.addView(readInstructionsButton); layout.addView(AdditionalOptions); });
            layout.addView(back);
            setContentView(layout);
        });

        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(commandInput); layout.addView(saveButton); layout.addView(keyboardSettingsButton); layout.addView(chooseKeyboardButton);
        layout.addView(new Button(this) {{ setText(isRussianDevice ? "Выбрать языки" : "Select languages"); setOnClickListener(v -> showLanguageSelectionDialog()); }});
        layout.addView(readInstructionsButton); layout.addView(AdditionalOptions);

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km.isKeyguardSecure()) {
            Intent intent = km.createConfirmDeviceCredentialIntent(null, null);
            if (intent != null) startActivityForResult(intent, 1337);
        } else { RESULT = true; setContentView(layout); }
    }

    private void showInstructions(boolean isRussianDevice) {
        String instr = isRussianDevice ? "Это приложение-клавиатура для экстренного стирания данных..." : "This is a keyboard app for emergency data wiping...";
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ScrollView scroll = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(instr); tv.setPadding(30,30,30,30); tv.setTextIsSelectable(true);
        scroll.addView(tv); builder.setView(scroll).setPositiveButton("OK", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1337 && resultCode == RESULT_OK) { RESULT = true; setContentView(layout); }
        else finish();
    }

    private void initializeDefaultLayoutsIfNeeded(boolean isRussianDevice) {
        Context dp = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences p = dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!p.contains(KEY_LAYOUT_RU)) p.edit().putString(KEY_LAYOUT_RU, string2DArrayToJson(new String[][]{{"1","2","3","4","5","6","7","8","9","0"},{"й","ц","у","к","е","н","г","ш","щ","з","х"},{"ф","ы","в","а","п","р","о","л","д","ж","э"},{"⇪","я","ч","с","м","и","т","ь","б","ю","⌫"},{"!#?","🌐",","," ",".","⏎"}})).apply();
        if (!p.contains(KEY_LAYOUT_EN)) p.edit().putString(KEY_LAYOUT_EN, string2DArrayToJson(new String[][]{{"1","2","3","4","5","6","7","8","9","0"},{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"⇪","z","x","c","v","b","n","m","⌫"},{"!#?","🌐",","," ",".","⏎"}})).apply();
        if (!p.contains(KEY_LAYOUT_SYM)) p.edit().putString(KEY_LAYOUT_SYM, string2DArrayToJson(new String[][]{{"1","2","3","4","5","6","7","8","9","0"},{"/","\\","`","+","*","@","#","$","^","&","'"},{"=","|","<",">","[","]","(",")","{","}","\""},{"😃","~","%","-","—","_",":",";","!","?","⌫"},{"abc","🌐",","," ",".","⏎"}})).apply();
    }

    private void initializeDefaultLanguageFlagsIfNeeded(boolean isRussianDevice) {
        Context dp = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences p = dp.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!p.contains(KEY_LANG_RU)) { p.edit().putBoolean(KEY_LANG_RU, isRussianDevice).putBoolean(KEY_LANG_EN, true).putBoolean(KEY_LANG_SYM, true).putBoolean(KEY_LANG_EMOJI, true).apply(); }
    }

    private String string2DArrayToJson(String[][] arr) {
        JSONArray outer = new JSONArray();
        try { for (String[] row : arr) { JSONArray inner = new JSONArray(); for (String s : row) inner.put(s); outer.put(inner); } } catch (Exception e) {}
        return outer.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { noNetworkWipeSwitch.setChecked(true); getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, true).apply(); }
    }

    public static String getCustomCommand(Context context) {
        return context.getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CUSTOM_COMMAND, "");
    }
}
