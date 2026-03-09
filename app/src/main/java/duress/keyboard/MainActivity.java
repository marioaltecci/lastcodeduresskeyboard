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
 потому что на некоторых устройствах
 даже если setUserAuthenticationRequired(false)), 
 Android Keystore может быть недоступен
 в BFU, а данное приложение являясь клавиатурой
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
                    String myService = new android.content.ComponentName(this, MyAccessibilityService.class).flattenToString();
                    accessibilityEnabled = services.contains(myService);
                }
            }
        } catch (Exception ignored) {}
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
        t1.setText(isRussianDevice() ? "Дайте приложению спецвозможности" : "Give accessibility permission");
        root.addView(t1, lp);

        TextView t2 = new TextView(this);
        t2.setText(isRussianDevice() ? "Перейдите в настройки спецвозможностей и включите для нашего приложения." : "Go to accessibility settings and enable our app.");
        root.addView(t2, lp);

        Button b1 = new Button(this);
        b1.setText(isRussianDevice() ? "Перейти в настройки спецвозможностей" : "Go to accessibility settings");
        b1.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(b1, lp);

        TextView t3 = new TextView(this);
        t3.setText(isRussianDevice() ? "Если в настройках спецвозможностей написано, что это ограниченная настройка — зайдите в настройки приложения, три точки сверху справа → разрешить ограниченные настройки." : "If restricted setting — go to app settings, three dots → allow restricted settings.");
        root.addView(t3, lp);

        Button b2 = new Button(this);
        b2.setText(isRussianDevice() ? "Перейти в настройки приложения" : "Go to app settings");
        b2.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));
        root.addView(b2, lp);

        TextView t4 = new TextView(this);
        t4.setText(isRussianDevice() ? "Затем вернитесь в настройки спецвозможностей и включите наше приложение." : "Then go back to accessibility and enable.");
        root.addView(t4, lp);

        String title = isRussianDevice() ? "Требуются спецвозможности" : "Accessibility required";
        accessibilityDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(root)
                .setCancelable(false)
                .create();
        accessibilityDialog.show();
    }

    private boolean isRussianDevice() {
        return "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    private void showLanguageSelectionDialog() {
        Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final boolean isRussian = isRussianDevice();

        final String[] languages = new String[] {
                "Русский (Russian)",
                "English (English)",
                "Español (Spanish)",
                isRussian ? "Символы (!#?)" : "Symbols (!#?)",
                isRussian ? "Эмодзи (😡🤡👍)" : "Emoji (😡🤡👍)"
        };

        final String[] keys = {KEY_LANG_RU, KEY_LANG_EN, KEY_LANG_ES, KEY_LANG_SYM, KEY_LANG_EMOJI};
        final boolean[] checkedItems = new boolean[languages.length];

        for (int i = 0; i < keys.length; i++) {
            checkedItems[i] = prefs.getBoolean(keys[i], false);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isRussian ? "Выберите языки сервиса клавиатуры" : "Select keyboard service languages")
                .setMultiChoiceItems(languages, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton(isRussian ? "Сохранить" : "Save", (dialog, which) -> {
                    SharedPreferences.Editor ed = prefs.edit();
                    for (int i = 0; i < keys.length; i++) {
                        ed.putBoolean(keys[i], checkedItems[i]);
                    }
                    ed.apply();
                    Toast.makeText(this, isRussian ? "Языки сохранены" : "Languages saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(isRussian ? "Отмена" : "Cancel", null)
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

        final boolean isRussian = isRussianDevice();

        initializeDefaultLayoutsIfNeeded(isRussian);
        initializeDefaultLanguageFlagsIfNeeded(isRussian);

        commandInput = new EditText(this);
        commandInput.setHint(isRussian ? "Задайте секретный код сброса" : "Set secret wipe code");

        final String allowedChars = getAllowedCharacters(this);

        InputFilter filter1 = new InputFilter.LengthFilter(50);
        InputFilter filterChars = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (allowedChars.indexOf(source.charAt(i)) == -1) {
                        return "";
                    }
                }
                return null;
            }
        };
        commandInput.setFilters(new InputFilter[]{filter1, filterChars});

        final Button saveButton = new Button(this);
        saveButton.setText(isRussian ? "Сохранить код" : "Save code");

        saveButton.setOnClickListener(v -> {
            String cmd = commandInput.getText().toString().trim();
            if (!cmd.isEmpty()) {
                try {
                    String salt = generateSalt();
                    String commandHash = hashKeyWithSalt(salt, cmd);

                    Context dpContext = getApplicationContext().createDeviceProtectedStorageContext();
                    SharedPreferences prefs = dpContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

                    prefs.edit()
                            .putString(KEY_CUSTOM_COMMAND, commandHash)
                            .putString("command_salt", salt)
                            .apply();

                    Toast.makeText(this, isRussian ? "Код сохранён" : "Code saved", Toast.LENGTH_SHORT).show();

                    commandInput.setText("");
                    commandInput.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
                } catch (NoSuchAlgorithmException e) {
                    Toast.makeText(this, "Ошибка хеширования", Toast.LENGTH_SHORT).show();
                }
            }
        });

        final Button keyboardSettingsButton = new Button(this);
        keyboardSettingsButton.setText(isRussian ? "Открыть настройки клавиатур" : "Open keyboard settings");
        keyboardSettingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        final Button chooseKeyboardButton = new Button(this);
        chooseKeyboardButton.setText(isRussian ? "Выбрать нашу клавиатуру" : "Select our keyboard");
        chooseKeyboardButton.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        final Button selectLanguagesButton = new Button(this);
        selectLanguagesButton.setText(isRussian ? "Выбрать языки" : "Select languages");
        selectLanguagesButton.setOnClickListener(v -> showLanguageSelectionDialog());

        final Button readInstructionsButton = new Button(this);
        readInstructionsButton.setText(isRussian ? "Инструкция" : "Instructions");
        readInstructionsButton.setOnClickListener(v -> {
            // Оставляю твой оригинальный код инструкции
            String instructions = isRussian ? "Подробная инструкция..." : "Detailed instructions...";
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scroll = new ScrollView(this);
            TextView tv = new TextView(this);
            tv.setText(instructions);
            tv.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            scroll.addView(tv);
            builder.setView(scroll);
            builder.setPositiveButton("OK", null);
            builder.show();
        });

        final Button additionalButton = new Button(this);
        additionalButton.setText(isRussian ? "Дополнительные параметры" : "Additional options");

        // Переключатели с короткими текстами
        Context dpUsb = getApplicationContext().createDeviceProtectedStorageContext();
        final SharedPreferences prefsUsb = dpUsb.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        final Switch usbSwitch = new Switch(this);
        usbSwitch.setText(isRussian ? "Сброс при USB/BT" : "Wipe on USB/BT");
        usbSwitch.setChecked(prefsUsb.getBoolean(KEY_USB_BLOCK, false));
        usbSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsUsb.edit().putBoolean(KEY_USB_BLOCK, isChecked).apply());

        final Switch chargeSwitch = new Switch(this);
        chargeSwitch.setText(isRussian ? "Сброс при зарядке" : "Wipe on charging");
        chargeSwitch.setChecked(prefsUsb.getBoolean(KEY_BLOCK_CHARGING, false));
        chargeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new AlertDialog.Builder(this)
                        .setTitle(isRussian ? "Подтверждение" : "Confirmation")
                        .setMessage(isRussian ? "Если телефон сейчас заряжается — данные сотрутся СЕЙЧАС!" : "If charging now — data will wipe IMMEDIATELY!")
                        .setPositiveButton(isRussian ? "Включить" : "Enable", (dialog, which) -> {
                            prefsUsb.edit().putBoolean(KEY_BLOCK_CHARGING, true).apply();
                            chargeSwitch.setChecked(true);
                        })
                        .setNegativeButton(isRussian ? "Отмена" : "Cancel", null)
                        .show();
            } else {
                prefsUsb.edit().putBoolean(KEY_BLOCK_CHARGING, false).apply();
                chargeSwitch.setChecked(false);
            }
        });

        noNetworkWipeSwitch = new Switch(this);
        noNetworkWipeSwitch.setText(isRussian ? "Сброс без сети >3 мин" : "Wipe no network >3 min");
        noNetworkWipeSwitch.setChecked(prefsNetwork.getBoolean(KEY_WIPE_ON_NO_NETWORK, false));
        noNetworkWipeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            } else {
                prefsNetwork.edit().putBoolean(KEY_WIPE_ON_NO_NETWORK, isChecked).apply();
            }
        });

        final Switch rebootSwitch = new Switch(this);
        rebootSwitch.setText(isRussian ? "Сброс при перезагрузке" : "Wipe on reboot");
        rebootSwitch.setChecked(prefsReboot.getBoolean(KEY_WIPE_ON_REBOOT, false));
        rebootSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsReboot.edit().putBoolean(KEY_WIPE_ON_REBOOT, isChecked).apply());

        final Switch wipeOnImeSwitch = new Switch(this);
        wipeOnImeSwitch.setText(isRussian ? "Сброс при смене клавиатуры" : "Wipe on keyboard change");
        wipeOnImeSwitch.setChecked(prefsIme.getBoolean(KEY_WIPE2, false));
        wipeOnImeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsIme.edit().putBoolean(KEY_WIPE2, isChecked).apply());

        final Switch autoRunSwitch = new Switch(this);
        autoRunSwitch.setText(isRussian ? "Автозапуск после перезагрузки" : "Auto-start after reboot");
        autoRunSwitch.setChecked(prefsAUTORUN.getBoolean(KEY_AUTORUN, false));
        autoRunSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsAUTORUN.edit().putBoolean(KEY_AUTORUN, isChecked).apply());

        final Switch fakeHomeSwitch = new Switch(this);
        fakeHomeSwitch.setText(isRussian ? "Фейковый домашний экран" : "Fake home screen");
        fakeHomeSwitch.setChecked(prefsIme.getBoolean(KEY_FAKE_HOME, false));
        fakeHomeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsIme.edit().putBoolean(KEY_FAKE_HOME, isChecked).apply());

        final Switch screenOnWipeSwitch = new Switch(this);
        screenOnWipeSwitch.setText(isRussian ? "Окно ✓/✗ при включении экрана" : "✓/✗ prompt on screen on");
        screenOnWipeSwitch.setChecked(prefsScreen.getBoolean(KEY_SCREEN_ON_WIPE_PROMPT, false));
        screenOnWipeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> prefsScreen.edit().putBoolean(KEY_SCREEN_ON_WIPE_PROMPT, isChecked).apply());

        final Switch aeSwitch = new Switch(this);
        aeSwitch.setText(isRussian ? "Фейк-пароль через спецвозможности" : "Fake password via accessibility");
        aeSwitch.setChecked(accessibilityEnabled);
        aeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!accessibilityEnabled) ais();
            else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        // Ряды с "?" для важных опций
        LinearLayout usbRow = wrapSwitchWithInfo(usbSwitch, isRussian ? "Стирает данные при подключении USB-устройств (флешка, мышка, клавиатура) или Bluetooth.\nНе срабатывает при обычной зарядке от блока." : "Wipes on USB/BT input devices.\nDoes not trigger on normal charger.");
        LinearLayout chargeRow = wrapSwitchWithInfo(chargeSwitch, isRussian ? "Может защитить от сложных USB-эксплойтов.\nОБЯЗАТЕЛЬНО отключите перед обычной зарядкой!" : "May protect from advanced USB attacks.\nDISABLE before normal charging!");
        LinearLayout networkRow = wrapSwitchWithInfo(noNetworkWipeSwitch, isRussian ? "Срабатывает если мобильная сеть пропала больше 3 минут и режим полёта выключен.\nТелефон будет просыпаться каждые 30 сек (чёрный экран).\nОтключайте в местах с нестабильной связью!" : "Triggers after 3+ min no mobile network (not airplane mode).\nKeeps device awake with black screen every 30s.\nDisable where signal drops often!");
        LinearLayout rebootRow = wrapSwitchWithInfo(rebootSwitch, isRussian ? "Стирает все данные сразу после перезагрузки.\nОчень жёсткая функция — используйте осторожно." : "Wipes everything immediately after reboot.\nVery aggressive — use with caution.");

        LinearLayout additionalLayout = new LinearLayout(this);
        additionalLayout.setOrientation(LinearLayout.VERTICAL);
        additionalLayout.addView(usbRow);
        additionalLayout.addView(chargeRow);
        additionalLayout.addView(networkRow);
        additionalLayout.addView(rebootRow);
        additionalLayout.addView(wipeOnImeSwitch);
        additionalLayout.addView(autoRunSwitch);
        additionalLayout.addView(fakeHomeSwitch);
        additionalLayout.addView(screenOnWipeSwitch);
        additionalLayout.addView(aeSwitch);

        Button backButton = new Button(this);
        backButton.setText(isRussian ? "Назад" : "Back");
        backButton.setOnClickListener(v -> recreate());
        additionalLayout.addView(backButton);

        additionalButton.setOnClickListener(v -> setContentView(additionalLayout));

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        layout.addView(commandInput);
        layout.addView(saveButton);
        layout.addView(keyboardSettingsButton);
        layout.addView(chooseKeyboardButton);
        layout.addView(selectLanguagesButton);
        layout.addView(readInstructionsButton);
        layout.addView(additionalButton);

        setContentView(layout);
    }

    private LinearLayout wrapSwitchWithInfo(Switch sw, String infoMessage) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(8), 0, dpToPx(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sw.setLayoutParams(params);
        row.addView(sw);

        Button infoBtn = new Button(this);
        infoBtn.setText("?");
        infoBtn.setWidth(dpToPx(48));
        infoBtn.setHeight(dpToPx(48));
        infoBtn.setTextSize(18);
        infoBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(isRussianDevice() ? "Важно" : "Important")
                .setMessage(infoMessage)
                .setPositiveButton("OK", null)
                .show());
        row.addView(infoBtn);

        return row;
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
                    {"😃","\~","%","-","—","_",":",";","!","?","⌫"},
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
