package com.jump.progression;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public final class MainActivity extends Activity {
    private static final int SAVE_XLS_REQUEST = 4101;
    private static final int PICK_VIDEO_REQUEST = 4102;
    private static final int SAVE_REPORT_REQUEST = 4103;
    private static final String[] LABELS = {"Дата", "Тренировка", "Упражнение", "Вес", "Подходы × повторы", "RPE", "Боль / дискомфорт 0–10", "Комментарий"};
    private static final String[] HINTS = {"ГГГГ-ММ-ДД", "Например, A", "Название упражнения", "Вес в кг", "", "0–10", "0–10", "Необязательно"};
    private static final int INK = Color.rgb(29, 48, 48);
    private static final int GREEN = Color.rgb(20, 125, 108);
    private static final int MUTED = Color.rgb(91, 110, 108);
    private WorkoutStore store;
    private ProgramA programA, programB, program;
    private String programCode = "A";
    private final List<String> liveAthletes = new ArrayList<>();
    private LinearLayout page;
    private String screen = "home";
    private String[] draft = new String[8];
    private String draftVideoUri = "";
    private String entryAthlete;
    private String journalAthlete;
    private String exportAthlete, exportFrom, exportTo;
    private boolean exportZip;
    private EditText exportFromField, exportToField;
    private int step = 0, week = 1, currentExercise = 0;
    private EditText field, setsField, repsField;
    private boolean[] completed;
    private List<Integer> liveIndices = new ArrayList<>();
    private String[][] liveAnswers;
    private String[] liveVideoUris;
    private String[] liveSetsReps;
    private int liveJournalIndex;
    private String liveJournalDate;
    private EditText liveWeightField, liveRpeField, livePainField, liveSetsRepsField;
    private boolean liveOwnWeight;
    private CountDownTimer timer;
    private TextView timerLabel;
    private int remainingSeconds;
    private Ringtone alarmSound;
    private ToneGenerator fallbackTone;
    private int alarmGeneration;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            screen = savedInstanceState.getString("screen", "home");
            exportAthlete = savedInstanceState.getString("export_athlete");
            exportFrom = savedInstanceState.getString("export_from");
            exportTo = savedInstanceState.getString("export_to");
            exportZip = savedInstanceState.getBoolean("export_zip");
            draft = savedInstanceState.getStringArray("draft");
            draftVideoUri = savedInstanceState.getString("draft_video_uri", "");
            entryAthlete = savedInstanceState.getString("entry_athlete");
            step = savedInstanceState.getInt("step");
            programCode = savedInstanceState.getString("program_code", "A");
            week = savedInstanceState.getInt("week", 1);
            currentExercise = savedInstanceState.getInt("current_exercise");
            completed = savedInstanceState.getBooleanArray("completed");
            ArrayList<Integer> restoredIndices = savedInstanceState.getIntegerArrayList("live_indices");
            if (restoredIndices != null) liveIndices = restoredIndices;
            ArrayList<String> restoredAthletes = savedInstanceState.getStringArrayList("live_athletes");
            if (restoredAthletes != null) liveAthletes.addAll(restoredAthletes);
            liveAnswers = (String[][]) savedInstanceState.getSerializable("live_answers");
            liveVideoUris = savedInstanceState.getStringArray("live_video_uris");
            liveSetsReps = savedInstanceState.getStringArray("live_sets_reps");
            liveJournalIndex = savedInstanceState.getInt("live_journal_index");
            liveJournalDate = savedInstanceState.getString("live_journal_date");
        }
        store = new WorkoutStore(this);
        try {
            programA = new ProgramA(this, "A");
            programB = new ProgramA(this, "B");
            program = "B".equals(programCode) ? programB : programA;
        } catch (IOException | JSONException error) {
            new android.app.AlertDialog.Builder(this).setTitle("Ошибка программы")
                    .setMessage("Не удалось загрузить упражнения: " + error.getMessage())
                    .setPositiveButton("Закрыть", (dialog, which) -> finish()).show();
            return;
        }
        if ("wizard".equals(screen) && draft != null && entryAthlete != null) wizard();
        else if ("live_log".equals(screen) && liveAnswers != null && liveVideoUris != null && liveSetsReps != null) liveJournal();
        else if ("live".equals(screen) && completed != null) live();
        else if ("export_range".equals(screen)) exportRange(exportAthlete);
        else home();
    }

    private int dp(int amount) { return (int) (amount * getResources().getDisplayMetrics().density + 0.5f); }

    private void begin(String title, String target) {
        screen = target;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 250, 248));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(32));
        scroll.addView(page);
        setContentView(scroll);
        TextView heading = text(title, 27, INK, true);
        page.addView(heading);
        gap(14);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void gap(int height) {
        View view = new View(this);
        page.addView(view, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void paragraph(String value) {
        TextView view = text(value, 16, MUTED, false);
        view.setLineSpacing(dp(3), 1f);
        page.addView(view);
        gap(12);
    }

    private void button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(16);
        view.setTextColor(Color.WHITE);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        view.setOnClickListener(v -> action.run());
        page.addView(view, new LinearLayout.LayoutParams(-1, dp(54)));
        gap(9);
    }

    private Button secondary(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setTextColor(INK);
        view.setOnClickListener(v -> action.run());
        page.addView(view, new LinearLayout.LayoutParams(-1, dp(48)));
        gap(7);
        return view;
    }

    private void home() {
        stopTimer();
        begin("Прогресс тренировок", "home");
        paragraph("Программы А и Б и журнал тренировок всегда под рукой.");
        button("Профили", this::manageProfiles);
        button("Создать запись тренировки", this::chooseAthleteForEntry);
        button("Журнал тренировок", this::chooseAthleteForJournal);
        button("Live-тренировка", this::startLive);
        button("Выгрузить XLS", () -> { exportZip = false; chooseExportScope(); });
        button("Отчёт для тренера (ZIP)", () -> { exportZip = true; chooseExportScope(); });
        gap(12);
        paragraph("Записи хранятся только на этом устройстве.");
    }

    private void manageProfiles() {
        begin("Профили", "profiles");
        paragraph("Создайте профиль для каждого участника. Журнал хранится отдельно для каждого профиля.");
        EditText name = input("Имя пользователя", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        button("Создать профиль", () -> {
            if (!store.createProfile(name.getText().toString())) {
                name.setError("Введите уникальное имя до 50 символов");
                return;
            }
            manageProfiles();
        });
        for (String athlete : store.profiles()) {
            page.addView(text(athlete, 19, INK, true));
            secondary("Удалить профиль · " + athlete, () -> new AlertDialog.Builder(this)
                    .setTitle("Удалить профиль «" + athlete + "»?")
                    .setMessage("Записи сохранятся в разделе «Записи без профиля». Их можно будет назначить другому профилю.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        store.deleteProfile(athlete);
                        manageProfiles();
                    }).show());
        }
        secondary("На главную", this::home);
    }

    private void chooseAthleteForEntry() {
        begin("Для кого запись?", "choose_entry_athlete");
        for (String athlete : store.profiles()) button(athlete, () -> newEntry(null, athlete));
        secondary("Создать профиль", this::manageProfiles);
        secondary("На главную", this::home);
    }

    private void newEntry(String[] previous, String athlete) {
        entryAthlete = athlete;
        draft = new String[8];
        draftVideoUri = "";
        draft[0] = previous == null ? LocalDate.now().toString() : previous[0];
        draft[1] = previous == null ? "A" : previous[1];
        draft[2] = previous == null ? program.exercises.get(0).name : "";
        for (int i = 3; i < draft.length; i++) draft[i] = "";
        step = 0;
        wizard();
    }

    private void wizard() {
        begin("Новая запись · " + entryAthlete, "wizard");
        paragraph("Поле " + (step + 1) + " из " + LABELS.length + " · " + LABELS[step]);
        field = null;
        setsField = null;
        repsField = null;
        if (step == 4) {
            String[] parts = draft[4].split("×", -1);
            page.addView(text("Подходы / круги", 17, INK, true));
            setsField = input("Сколько подходов", parts.length > 0 ? parts[0] : "", InputType.TYPE_CLASS_NUMBER);
            page.addView(text("Повторения в подходе", 17, INK, true));
            repsField = input("Сколько повторений", parts.length > 1 ? parts[1] : "", InputType.TYPE_CLASS_NUMBER);
        } else {
            String value = draft[step];
            if (step == 3 && value.endsWith(" кг")) value = value.substring(0, value.length() - 3);
            if (step == 3 && "Свой вес".equals(value)) value = "";
            int type = step == 7 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT;
            if (step == 3 || step == 5) type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            if (step == 6) type = InputType.TYPE_CLASS_NUMBER;
            field = input(HINTS[step], value, type);
            if (step == 3) secondary("Свой вес", () -> { draft[3] = "Свой вес"; step++; wizard(); });
        }
        gap(16);
        if (step == 0) secondary("Выбрать дату", this::pickDate);
        if (step == 1) {
            secondary("Программа А", () -> field.setText("A"));
            secondary("Программа Б", () -> field.setText("B"));
        }
        if (step == 2) {
            paragraph("Упражнение можно выбрать из программы А или Б либо ввести своё.");
            for (ProgramA.Exercise exercise : programA.exercises) secondary(exercise.name, () -> field.setText(exercise.name));
            for (ProgramA.Exercise exercise : programB.exercises) {
                if (programA.exercises.stream().noneMatch(item -> item.name.equals(exercise.name)))
                    secondary(exercise.name, () -> field.setText(exercise.name));
            }
        }
        if (step == 7) videoPickerControls(draftVideoUri);
        button(step == LABELS.length - 1 ? "Сохранить запись" : "Далее", () -> {
            captureWizardStep();
            if (!validStep()) return;
            if (step == LABELS.length - 1) save(); else { step++; wizard(); }
        });
        if (step > 0) secondary("Назад", () -> { captureWizardStep(); step--; wizard(); });
        secondary("На главную", this::home);
    }

    private EditText input(String hint, String value, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setSingleLine(type != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        edit.setText(value);
        edit.setTextSize(20);
        page.addView(edit, new LinearLayout.LayoutParams(-1, type == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE) ? dp(110) : dp(60)));
        gap(8);
        return edit;
    }

    private void videoPickerControls(String videoUri) {
        if (videoUri.isEmpty()) {
            secondary("Прикрепить видео исполнения", this::pickOwnVideo);
        } else {
            paragraph("Видео исполнения прикреплено");
            secondary("Заменить видео", this::pickOwnVideo);
            secondary("Убрать видео", () -> {
                if ("wizard".equals(screen)) {
                    captureWizardStep();
                    draftVideoUri = "";
                    wizard();
                } else {
                    captureLiveAnswer(false);
                    liveVideoUris[liveJournalIndex] = "";
                    liveJournal();
                }
            });
        }
    }

    private void pickOwnVideo() {
        if ("wizard".equals(screen)) captureWizardStep();
        if ("live_log".equals(screen)) captureLiveAnswer(false);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO_REQUEST);
    }

    private void openOwnVideo(String videoUri) {
        Intent intent = new Intent(this, VideoActivity.class);
        intent.putExtra(VideoActivity.EXTRA_VIDEO_URI, videoUri);
        startActivity(intent);
    }

    private void captureWizardStep() {
        if (step == 4) draft[4] = setsField.getText().toString().trim() + "×" + repsField.getText().toString().trim();
        else {
            String value = field.getText().toString().trim();
            if (step != 3 || !value.isEmpty() || !"Свой вес".equals(draft[3])) draft[step] = value;
        }
    }

    private void pickDate() {
        pickDate(field);
    }

    private void pickDate(EditText target) {
        Calendar today = Calendar.getInstance();
        try {
            LocalDate date = LocalDate.parse(target.getText().toString().trim());
            today.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        } catch (DateTimeParseException ignored) { }
        new DatePickerDialog(this, (picker, year, month, day) ->
                target.setText(LocalDate.of(year, month + 1, day).toString()),
                today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show();
    }

    private boolean validStep() {
        String value = draft[step];
        if (step == 0) {
            try { LocalDate.parse(value); } catch (DateTimeParseException error) { field.setError("Выберите корректную дату"); return false; }
        }
        if ((step == 1 || step == 2) && value.isEmpty()) { field.setError("Заполните поле"); return false; }
        if (step == 3 && !"Свой вес".equals(value)) {
            if (!validWeight(value)) { field.setError("Введите вес в кг или выберите «Свой вес»"); return false; }
            draft[3] = value.replace(',', '.') + " кг";
        }
        if (step == 4) {
            if (!positiveInt(setsField)) return false;
            if (!positiveInt(repsField)) return false;
        }
        if ((step == 5 || step == 6) && !value.isEmpty()) {
            try {
                double number = Double.parseDouble(value.replace(',', '.'));
                if (number < 0 || number > 10 || (step == 6 && number != Math.floor(number))) throw new NumberFormatException();
            } catch (NumberFormatException error) { field.setError("Введите число от 0 до 10" + (step == 6 ? " без дробной части" : "")); return false; }
        }
        return true;
    }

    private boolean positiveInt(EditText edit) {
        String value = edit.getText().toString().trim();
        try {
            if (!value.matches("[0-9]+") || Integer.parseInt(value) < 1) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            edit.setError("Введите целое число больше нуля");
            return false;
        }
        return true;
    }

    private boolean validWeight(String value) {
        if (!value.matches("[0-9]+([.,][0-9]+)?")) return false;
        try {
            double weight = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(weight) && weight > 0;
        }
        catch (NumberFormatException error) { return false; }
    }

    private void save() {
        try {
            store.add(entryAthlete, draft, draftVideoUri);
            String[] saved = draft.clone();
            begin("Запись сохранена", "saved");
            paragraph(entryAthlete + " · " + saved[0] + " · " + saved[2]);
            button("Добавить ещё упражнение", () -> newEntry(saved, entryAthlete));
            button("Посмотреть записи за дату", () -> entries(entryAthlete, saved[0]));
            secondary("На главную", this::home);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить запись: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseAthleteForJournal() {
        begin("Чей журнал открыть?", "choose_journal_athlete");
        for (String athlete : store.profiles()) button(athlete, () -> dates(athlete));
        if (store.hasUnassigned()) secondary("Записи без профиля", () -> dates(""));
        secondary("Создать профиль", this::manageProfiles);
        secondary("На главную", this::home);
    }

    private void dates(String athlete) {
        journalAthlete = athlete;
        begin(athlete.isEmpty() ? "Записи без профиля" : "Журнал · " + athlete, "dates");
        List<String> dates = store.dates(athlete);
        if (dates.isEmpty()) paragraph("Записей пока нет. Добавьте первое упражнение.");
        for (String date : dates) button(date, () -> entries(athlete, date));
        secondary("Выбрать другого", this::chooseAthleteForJournal);
        secondary("На главную", this::home);
    }

    private void entries(String athlete, String date) {
        journalAthlete = athlete;
        begin((athlete.isEmpty() ? "Записи без профиля" : athlete) + " · " + date, "entries");
        List<WorkoutStore.Entry> rows = store.onDate(athlete, date);
        for (WorkoutStore.Entry entry : rows) {
            String[] row = entry.values;
            TextView title = text(row[2], 20, INK, true);
            page.addView(title);
            gap(8);
            for (int i = 0; i < row.length; i++) {
                TextView value = text(LABELS[i] + ": " + (row[i].isEmpty() ? "—" : row[i]), 15, MUTED, false);
                page.addView(value);
                gap(4);
            }
            if (!entry.videoUri.isEmpty()) secondary("Смотреть видео исполнения", () -> openOwnVideo(entry.videoUri));
            secondary("Удалить запись · " + row[2], () -> new AlertDialog.Builder(this)
                    .setTitle("Удалить запись?")
                    .setMessage(row[2] + " · " + row[0] + "\nДействие нельзя отменить.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        if (store.delete(entry.id)) {
                            if (store.onDate(athlete, date).isEmpty()) dates(athlete); else entries(athlete, date);
                        } else Toast.makeText(this, "Запись не найдена", Toast.LENGTH_SHORT).show();
                    }).show());
            if (athlete.isEmpty()) {
                for (String profile : store.profiles())
                    secondary("Назначить: " + profile, () -> assignLegacy(entry, profile, date));
            }
            gap(20);
        }
        secondary("Все даты", () -> dates(athlete));
        secondary("На главную", this::home);
    }

    private void assignLegacy(WorkoutStore.Entry entry, String athlete, String date) {
        new AlertDialog.Builder(this).setTitle("Назначить запись " + athlete + "?")
                .setMessage(entry.values[2] + " · " + date)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Назначить", (dialog, which) -> {
                    if (store.assign(entry.id, athlete)) {
                        if (store.onDate("", date).isEmpty()) dates(""); else entries("", date);
                    } else Toast.makeText(this, "Запись не найдена", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void chooseExportScope() {
        begin(exportZip ? "Отчёт для тренера (ZIP)" : "Выгрузить записи в XLS", "export_scope");
        paragraph("Сначала выберите, чьи записи включить в файл.");
        for (String athlete : store.profiles()) button(athlete, () -> exportRange(athlete));
        button("Все записи", () -> exportRange(null));
        secondary("На главную", this::home);
    }

    private void exportRange(String athlete) {
        exportAthlete = athlete;
        if (exportFrom == null) exportFrom = LocalDate.now().withDayOfMonth(1).toString();
        if (exportTo == null) exportTo = LocalDate.now().toString();
        begin("Период выгрузки", "export_range");
        paragraph("Диапазон включает обе выбранные даты. После этого выберите папку на телефоне, например «Загрузки».");
        page.addView(text("С даты", 17, INK, true));
        exportFromField = input("ГГГГ-ММ-ДД", exportFrom, InputType.TYPE_CLASS_TEXT);
        secondary("Выбрать начальную дату", () -> pickDate(exportFromField));
        page.addView(text("По дату", 17, INK, true));
        exportToField = input("ГГГГ-ММ-ДД", exportTo, InputType.TYPE_CLASS_TEXT);
        secondary("Выбрать конечную дату", () -> pickDate(exportToField));
        button("Выбрать место сохранения", () -> {
            String from = exportFromField.getText().toString().trim();
            String to = exportToField.getText().toString().trim();
            LocalDate fromDate, toDate;
            try { fromDate = LocalDate.parse(from); }
            catch (DateTimeParseException error) { exportFromField.setError("Выберите корректную дату"); return; }
            try { toDate = LocalDate.parse(to); }
            catch (DateTimeParseException error) { exportToField.setError("Выберите корректную дату"); return; }
            if (fromDate.isAfter(toDate)) { exportToField.setError("Конечная дата раньше начальной"); return; }
            if (store.exportRows(exportAthlete, from, to).isEmpty()) {
                Toast.makeText(this, "За этот период записей нет", Toast.LENGTH_LONG).show();
                return;
            }
            exportFrom = from;
            exportTo = to;
            String scope = athlete == null ? "all" : "profile";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(exportZip ? "application/zip" : "application/vnd.ms-excel");
            intent.putExtra(Intent.EXTRA_TITLE, "training-" + scope + "-" + from + "-" + to + (exportZip ? ".zip" : ".xls"));
            startActivityForResult(intent, exportZip ? SAVE_REPORT_REQUEST : SAVE_XLS_REQUEST);
        });
        secondary("Назад", this::chooseExportScope);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if ("wizard".equals(screen) && (field != null || setsField != null))
            captureWizardStep();
        if ("live_log".equals(screen) && liveWeightField != null) captureLiveAnswer(false);
        if (exportFromField != null && "export_range".equals(screen)) {
            exportFrom = exportFromField.getText().toString().trim();
            exportTo = exportToField.getText().toString().trim();
        }
        state.putString("export_athlete", exportAthlete);
        state.putString("export_from", exportFrom);
        state.putString("export_to", exportTo);
        state.putBoolean("export_zip", exportZip);
        state.putString("screen", screen);
        state.putStringArray("draft", draft);
        state.putString("draft_video_uri", draftVideoUri);
        state.putString("entry_athlete", entryAthlete);
        state.putInt("step", step);
        state.putString("program_code", programCode);
        state.putInt("week", week);
        state.putInt("current_exercise", currentExercise);
        state.putBooleanArray("completed", completed);
        state.putIntegerArrayList("live_indices", new ArrayList<>(liveIndices));
        state.putStringArrayList("live_athletes", new ArrayList<>(liveAthletes));
        state.putSerializable("live_answers", liveAnswers);
        state.putStringArray("live_video_uris", liveVideoUris);
        state.putStringArray("live_sets_reps", liveSetsReps);
        state.putInt("live_journal_index", liveJournalIndex);
        state.putString("live_journal_date", liveJournalDate);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if ("live_log".equals(screen)) {
                        liveVideoUris[liveJournalIndex] = data.getData().toString();
                        liveJournal();
                    } else if ("wizard".equals(screen)) {
                        captureWizardStep();
                        draftVideoUri = data.getData().toString();
                        wizard();
                    }
                } catch (SecurityException error) {
                    Toast.makeText(this, "Не удалось сохранить доступ к видео", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if ((requestCode != SAVE_XLS_REQUEST && requestCode != SAVE_REPORT_REQUEST)
                || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        if (requestCode == SAVE_REPORT_REQUEST) {
            List<WorkoutStore.Entry> rows = store.exportRows(exportAthlete, exportFrom, exportTo);
            begin("Создаём ZIP-отчёт", "exporting");
            paragraph("Копируем видео. Дождитесь сообщения о сохранении и не закрывайте приложение.");
            new Thread(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IOException("Не удалось открыть выбранный файл");
                    TrainerReportExporter.write(this, output, rows);
                    runOnUiThread(() -> {
                        begin("ZIP-отчёт сохранён", "report_ready");
                        paragraph("Отправьте архив тренеру целиком. После распаковки он сможет открыть index.html и посмотреть видео.");
                        button("Поделиться ZIP", () -> shareReport(destination));
                        secondary("На главную", this::home);
                    });
                } catch (Exception error) {
                    try { DocumentsContract.deleteDocument(getContentResolver(), destination); }
                    catch (Exception ignored) { }
                    runOnUiThread(() -> {
                        begin("Ошибка создания отчёта", "export_error");
                        paragraph("Не удалось сохранить отчёт: " + error.getMessage());
                        secondary("Повторить", this::chooseExportScope);
                        secondary("На главную", this::home);
                    });
                }
            }).start();
            return;
        }
        File temporary = null;
        try {
            List<WorkoutStore.Entry> rows = store.exportRows(exportAthlete, exportFrom, exportTo);
            temporary = File.createTempFile("training-export-", ".xls", getCacheDir());
            try (OutputStream output = new FileOutputStream(temporary)) { XlsExporter.write(output, rows); }
            try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("Не удалось открыть выбранный файл");
                Files.copy(temporary.toPath(), output);
            }
            Toast.makeText(this, "XLS сохранён в выбранную папку", Toast.LENGTH_LONG).show();
            home();
        } catch (Exception error) {
            try { DocumentsContract.deleteDocument(getContentResolver(), destination); }
            catch (Exception ignored) { }
            Toast.makeText(this, "Не удалось сохранить XLS: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private void shareReport(Uri destination) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, destination);
        intent.setClipData(ClipData.newUri(getContentResolver(), "Отчёт о тренировках", destination));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Поделиться отчётом"));
    }

    private void startLive() {
        begin("Выберите программу", "choose_program");
        button("Программа А", () -> beginLive("A"));
        button("Программа Б", () -> beginLive("B"));
        secondary("На главную", this::home);
    }

    private void beginLive(String code) {
        programCode = code;
        program = "B".equals(code) ? programB : programA;
        week = 1;
        currentExercise = 0;
        completed = new boolean[program.exercises.size()];
        live();
    }

    private int completedCount() {
        int count = 0;
        if (completed != null) for (boolean done : completed) if (done) count++;
        return count;
    }

    private void selectWeek(int number) {
        if (number == week) return;
        if (completedCount() > 0) {
            new AlertDialog.Builder(this).setTitle("Сменить неделю?")
                    .setMessage("Отметки выполненных упражнений будут сброшены.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сменить", (dialog, which) -> {
                        week = number;
                        completed = new boolean[program.exercises.size()];
                        currentExercise = 0;
                        live();
                    }).show();
        } else { week = number; currentExercise = 0; live(); }
    }

    private void live() {
        stopTimer();
        begin("Live-тренировка · " + programCode, "live");
        paragraph("Выберите неделю. Упражнения идут в порядке таблицы, после 5–8 минут разминки и мобилизации.");
        LinearLayout weeks = new LinearLayout(this);
        weeks.setOrientation(LinearLayout.HORIZONTAL);
        for (int selected = 1; selected <= 4; selected++) {
            final int number = selected;
            Button choice = new Button(this);
            choice.setText("" + selected);
            choice.setContentDescription("Неделя " + selected);
            choice.setTextColor(selected == week ? Color.WHITE : INK);
            if (selected == week) choice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
            choice.setOnClickListener(v -> selectWeek(number));
            weeks.addView(choice, new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        page.addView(weeks);
        gap(18);
        paragraph("Выполнено: " + completedCount() + " из " + program.exercises.size() + ". Отмечайте упражнение после его завершения.");
        ProgramA.Exercise exercise = program.exercises.get(currentExercise);
        page.addView(text((currentExercise + 1) + " / " + program.exercises.size() + " · " + exercise.block, 16, GREEN, true));
        gap(8);
        page.addView(text(exercise.name, 24, INK, true));
        gap(10);
        page.addView(text("Неделя " + week + ": " + exercise.weeks[week - 1], 22, INK, true));
        gap(10);
        paragraph("Отдых: " + exercise.rest + "\n\n" + exercise.focus);
        secondary("Посмотреть видео упражнения", () -> {
            Intent intent = new Intent(this, VideoActivity.class);
            intent.putExtra(VideoActivity.EXTRA_PROGRAM, programCode);
            intent.putExtra(VideoActivity.EXTRA_EXERCISE_INDEX, currentExercise);
            intent.putExtra(VideoActivity.EXTRA_EXERCISE_NAME, exercise.name);
            startActivity(intent);
        });
        timerLabel = text("", 22, GREEN, true);
        timerLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(timerLabel);
        gap(8);
        button("Отдых · " + exercise.rest, () -> startRest(exercise.restSeconds()));
        button(completed[currentExercise] ? "✓ Выполнено · снять отметку" : "Упражнение выполнено", () -> {
            completed[currentExercise] = !completed[currentExercise];
            boolean allDone = completedCount() == program.exercises.size();
            if (completed[currentExercise] && !allDone) {
                for (int offset = 1; offset < completed.length; offset++) {
                    int index = (currentExercise + offset) % completed.length;
                    if (!completed[index]) { currentExercise = index; break; }
                }
            }
            live();
            if (allDone) offerLiveJournal();
        });
        if (currentExercise > 0) secondary("Предыдущее упражнение", () -> { currentExercise--; live(); });
        if (currentExercise < program.exercises.size() - 1) button("Следующее упражнение", () -> { currentExercise++; live(); });
        else paragraph("Это последнее упражнение программы " + programCode + ".");
        button("Завершить тренировку", this::offerLiveJournal);
        gap(10);
        page.addView(text("Порядок упражнений", 19, INK, true));
        gap(10);
        for (int i = 0; i < program.exercises.size(); i++) {
            ProgramA.Exercise item = program.exercises.get(i);
            final int index = i;
            secondary((completed[i] ? "✓ " : i == currentExercise ? "● " : "") + (i + 1) + ". " + item.name + " · " + item.weeks[week - 1],
                    () -> { currentExercise = index; live(); });
        }
        secondary("На главную", () -> { if (completedCount() > 0) offerLiveJournal(); else home(); });
    }

    private void offerLiveJournal() {
        int count = completedCount();
        if (count == 0) {
            Toast.makeText(this, "Сначала отметьте выполненные упражнения", Toast.LENGTH_LONG).show();
            return;
        }
        stopTimer();
        new AlertDialog.Builder(this).setTitle("Тренировка завершена")
                .setMessage("Выполнено упражнений: " + count + ". Заполнить журнал для участников?")
                .setPositiveButton("Выбрать участников", (dialog, which) -> chooseLiveParticipants())
                .setNegativeButton("Продолжить", null)
                .setNeutralButton("Без записи", (dialog, which) -> home()).show();
    }

    private void chooseLiveParticipants() {
        begin("Участники тренировки", "live_participants");
        List<String> profiles = store.profiles();
        if (profiles.isEmpty()) paragraph("Сначала создайте профиль, чтобы сохранить тренировку.");
        List<CheckBox> choices = new ArrayList<>();
        for (String name : profiles) {
            CheckBox choice = new CheckBox(this);
            choice.setText(name);
            choice.setTextSize(19);
            page.addView(choice);
            choices.add(choice);
        }
        if (!profiles.isEmpty()) button("Заполнить журнал", () -> {
            liveAthletes.clear();
            for (int i = 0; i < profiles.size(); i++) if (choices.get(i).isChecked()) liveAthletes.add(profiles.get(i));
            if (liveAthletes.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одного участника", Toast.LENGTH_SHORT).show();
                return;
            }
            startLiveJournal();
        });
        secondary("Вернуться к тренировке", this::live);
        secondary("На главную", this::home);
    }

    private void startLiveJournal() {
        liveIndices = new ArrayList<>();
        for (int i = 0; i < completed.length; i++) if (completed[i]) liveIndices.add(i);
        liveAnswers = new String[liveIndices.size() * liveAthletes.size()][3];
        liveVideoUris = new String[liveAnswers.length];
        liveSetsReps = new String[liveAnswers.length];
        for (String[] answer : liveAnswers) for (int i = 0; i < 3; i++) answer[i] = "";
        for (int i = 0; i < liveVideoUris.length; i++) liveVideoUris[i] = "";
        for (int i = 0; i < liveSetsReps.length; i++)
            liveSetsReps[i] = program.exercises.get(liveIndices.get(i % liveIndices.size())).weeks[week - 1];
        liveJournalDate = LocalDate.now().toString();
        liveJournalIndex = 0;
        liveJournal();
    }

    private void liveJournal() {
        begin("Журнал после тренировки", "live_log");
        int exercisesPerAthlete = liveIndices.size();
        int participant = liveJournalIndex / exercisesPerAthlete;
        String athlete = liveAthletes.get(participant);
        ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(liveJournalIndex % exercisesPerAthlete));
        String[] answer = liveAnswers[liveJournalIndex];
        page.addView(text(athlete, 21, GREEN, true));
        gap(8);
        paragraph("Сегодня · " + liveJournalDate + " · упражнение " + (liveJournalIndex % exercisesPerAthlete + 1) + " из " + exercisesPerAthlete);
        page.addView(text(exercise.name, 22, INK, true));
        gap(8);
        paragraph("План недели " + week + ": " + exercise.weeks[week - 1]);
        page.addView(text("Фактически выполнено · подходы × повторы", 17, INK, true));
        liveSetsRepsField = input("Например, 3×8/сторона", liveSetsReps[liveJournalIndex], InputType.TYPE_CLASS_TEXT);
        page.addView(text("Вес", 17, INK, true));
        liveOwnWeight = "Свой вес".equals(answer[0]);
        String weight = answer[0].endsWith(" кг") ? answer[0].substring(0, answer[0].length() - 3) : "";
        liveWeightField = input("Вес в кг", weight, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        liveWeightField.setEnabled(!liveOwnWeight);
        if (liveOwnWeight) liveWeightField.setHint("Выбран свой вес");
        Button ownButton = secondary(liveOwnWeight ? "✓ Свой вес · изменить" : "Свой вес", () -> {});
        ownButton.setOnClickListener(v -> {
            liveOwnWeight = !liveOwnWeight;
            liveWeightField.setEnabled(!liveOwnWeight);
            liveWeightField.setHint(liveOwnWeight ? "Выбран свой вес" : "Вес в кг");
            if (liveOwnWeight) liveWeightField.setText("");
            ownButton.setText(liveOwnWeight ? "✓ Свой вес · изменить" : "Свой вес");
        });
        page.addView(text("RPE", 17, INK, true));
        liveRpeField = input("0–10", answer[1], InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        page.addView(text("Боль / дискомфорт", 17, INK, true));
        livePainField = input("0–10", answer[2], InputType.TYPE_CLASS_NUMBER);
        videoPickerControls(liveVideoUris[liveJournalIndex]);
        String nextLabel = liveJournalIndex == liveAnswers.length - 1 ? "Сохранить журнал" :
                liveJournalIndex % exercisesPerAthlete == exercisesPerAthlete - 1
                        ? "Следующий участник: " + liveAthletes.get(participant + 1) : "Следующее упражнение";
        button(nextLabel, () -> {
            if (!captureLiveAnswer(true)) return;
            if (liveJournalIndex == liveAnswers.length - 1) saveLiveJournal();
            else { liveJournalIndex++; liveJournal(); }
        });
        if (liveJournalIndex > 0) secondary("Предыдущее упражнение", () -> {
            captureLiveAnswer(false);
            liveJournalIndex--;
            liveJournal();
        });
        secondary("Вернуться к тренировке", this::confirmDiscardLiveJournal);
    }

    private boolean captureLiveAnswer(boolean validate) {
        String weight = liveOwnWeight ? "Свой вес" : liveWeightField.getText().toString().trim();
        String rpe = liveRpeField.getText().toString().trim();
        String pain = livePainField.getText().toString().trim();
        String setsReps = liveSetsRepsField.getText().toString().trim();
        if (validate) {
            if (setsReps.isEmpty()) { liveSetsRepsField.setError("Укажите фактические подходы и повторы"); return false; }
            if (!liveOwnWeight && !validWeight(weight)) { liveWeightField.setError("Введите кг или выберите «Свой вес»"); return false; }
            if (!validRating(rpe, false)) { liveRpeField.setError("Число от 0 до 10"); return false; }
            if (!validRating(pain, true)) { livePainField.setError("Целое число от 0 до 10"); return false; }
        }
        liveAnswers[liveJournalIndex][0] = liveOwnWeight || weight.isEmpty() ? weight : weight.replace(',', '.') + " кг";
        liveAnswers[liveJournalIndex][1] = rpe;
        liveAnswers[liveJournalIndex][2] = pain;
        liveSetsReps[liveJournalIndex] = setsReps;
        return true;
    }

    private boolean validRating(String value, boolean integer) {
        if (value.isEmpty() || !(integer ? value.matches("[0-9]+") : value.matches("[0-9]+([.,][0-9]+)?"))) return false;
        try {
            double number = Double.parseDouble(value.replace(',', '.'));
            return number >= 0 && number <= 10;
        } catch (NumberFormatException error) { return false; }
    }

    private void saveLiveJournal() {
        List<WorkoutStore.NewEntry> records = new ArrayList<>();
        int exercisesPerAthlete = liveIndices.size();
        for (int i = 0; i < liveAnswers.length; i++) {
            String athlete = liveAthletes.get(i / exercisesPerAthlete);
            ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(i % exercisesPerAthlete));
            String[] answer = liveAnswers[i];
            records.add(new WorkoutStore.NewEntry(athlete,
                    new String[]{liveJournalDate, programCode, exercise.name, answer[0], liveSetsReps[i], answer[1], answer[2], ""},
                    liveVideoUris[i]));
        }
        try {
            store.addAll(records);
            begin("Журнал сохранён", "saved");
            paragraph("Добавлено по " + exercisesPerAthlete + " упражнений для " + liveAthletes.size() + " участников · " + liveJournalDate);
            for (String athlete : liveAthletes) button("Журнал · " + athlete, () -> entries(athlete, liveJournalDate));
            secondary("На главную", this::home);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить журнал: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDiscardLiveJournal() {
        new AlertDialog.Builder(this).setTitle("Вернуться к тренировке?")
                .setMessage("Данные журнала пока не сохранены.")
                .setNegativeButton("Остаться", null)
                .setPositiveButton("Вернуться", (dialog, which) -> live()).show();
    }

    private void startRest(int seconds) {
        stopTimer();
        if (seconds <= 0) return;
        remainingSeconds = seconds;
        timerLabel.setText("Осталось " + remainingSeconds + " сек");
        timer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override public void onTick(long millis) {
                remainingSeconds = (int) Math.ceil(millis / 1000.0);
                timerLabel.setText("Осталось " + remainingSeconds + " сек");
            }
            @Override public void onFinish() {
                remainingSeconds = 0;
                timer = null;
                timerLabel.setText("Отдых закончен");
                playAlarm();
            }
        }.start();
    }

    private void playAlarm() {
        stopAlarm();
        int generation = alarmGeneration;
        try {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (sound != null) {
                alarmSound = RingtoneManager.getRingtone(this, sound);
                if (alarmSound != null) {
                    alarmSound.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                    alarmSound.play();
                }
            }
        } catch (RuntimeException error) {
            if (alarmSound != null) alarmSound.stop();
            alarmSound = null;
        }
        if (alarmSound == null) {
            try {
                fallbackTone = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
                for (int i = 0; i < 3; i++) {
                    page.postDelayed(() -> {
                        if (generation == alarmGeneration && fallbackTone != null)
                            fallbackTone.startTone(ToneGenerator.TONE_PROP_BEEP, 650);
                    }, i * 900L);
                }
            } catch (RuntimeException ignored) { fallbackTone = null; }
        }
        page.postDelayed(() -> { if (generation == alarmGeneration) stopAlarm(); }, 5000);
    }

    private void stopAlarm() {
        alarmGeneration++;
        if (alarmSound != null) { alarmSound.stop(); alarmSound = null; }
        if (fallbackTone != null) { fallbackTone.release(); fallbackTone = null; }
    }

    private void stopTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
        stopAlarm();
    }

    @Override public void onBackPressed() {
        if ("entries".equals(screen)) dates(journalAthlete);
        else if ("dates".equals(screen)) chooseAthleteForJournal();
        else if ("wizard".equals(screen) && step > 0) { captureWizardStep(); step--; wizard(); }
        else if ("wizard".equals(screen)) chooseAthleteForEntry();
        else if ("export_range".equals(screen)) chooseExportScope();
        else if ("live_log".equals(screen) && liveJournalIndex > 0) {
            captureLiveAnswer(false);
            liveJournalIndex--;
            liveJournal();
        }
        else if ("live_log".equals(screen)) confirmDiscardLiveJournal();
        else if ("live".equals(screen) && completedCount() > 0) offerLiveJournal();
        else if ("home".equals(screen)) super.onBackPressed();
        else home();
    }

    @Override protected void onDestroy() {
        stopTimer();
        store.close();
        super.onDestroy();
    }
}
