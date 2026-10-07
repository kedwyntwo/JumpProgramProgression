package com.jump.progression;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
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
    private static final String[] LABELS = {"Дата", "Тренировка", "Упражнение", "Вес", "Подходы × повторы", "RPE", "Боль / дискомфорт 0–10", "Комментарий"};
    private static final String[] HINTS = {"ГГГГ-ММ-ДД", "Например, A", "Название упражнения", "Вес в кг", "", "0–10", "0–10", "Необязательно"};
    private static final int INK = Color.rgb(29, 48, 48);
    private static final int GREEN = Color.rgb(20, 125, 108);
    private static final int MUTED = Color.rgb(91, 110, 108);
    private WorkoutStore store;
    private ProgramA program;
    private LinearLayout page;
    private String screen = "home";
    private String[] draft = new String[8];
    private String entryAthlete;
    private String journalAthlete;
    private String exportAthlete, exportFrom, exportTo;
    private EditText exportFromField, exportToField;
    private int step = 0, week = 1, currentExercise = 0;
    private EditText field, setsField, repsField;
    private boolean[] completed;
    private List<Integer> liveIndices = new ArrayList<>();
    private String[][] liveAnswers;
    private int liveJournalIndex;
    private String liveJournalDate;
    private EditText liveWeightField, liveRpeField, livePainField;
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
            exportAthlete = savedInstanceState.getString("export_athlete");
            exportFrom = savedInstanceState.getString("export_from");
            exportTo = savedInstanceState.getString("export_to");
        }
        store = new WorkoutStore(this);
        try {
            program = new ProgramA(this);
        } catch (IOException | JSONException error) {
            new android.app.AlertDialog.Builder(this).setTitle("Ошибка программы")
                    .setMessage("Не удалось загрузить упражнения: " + error.getMessage())
                    .setPositiveButton("Закрыть", (dialog, which) -> finish()).show();
            return;
        }
        home();
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
        paragraph("Программа A и журнал тренировок всегда под рукой.");
        button("Создать запись тренировки", this::chooseAthleteForEntry);
        button("Журнал тренировок", this::chooseAthleteForJournal);
        button("Live-тренировка", this::startLive);
        button("Выгрузить XLS", this::chooseExportScope);
        gap(12);
        paragraph("Записи хранятся только на этом устройстве.");
    }

    private void chooseAthleteForEntry() {
        begin("Для кого запись?", "choose_entry_athlete");
        button(WorkoutStore.KIRILL, () -> newEntry(null, WorkoutStore.KIRILL));
        button(WorkoutStore.IGOR, () -> newEntry(null, WorkoutStore.IGOR));
        secondary("На главную", this::home);
    }

    private void newEntry(String[] previous, String athlete) {
        entryAthlete = athlete;
        draft = new String[8];
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
        if (step == 2) {
            paragraph("Упражнение можно выбрать из программы A или ввести своё.");
            for (ProgramA.Exercise exercise : program.exercises) secondary(exercise.name, () -> field.setText(exercise.name));
        }
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
            store.add(entryAthlete, draft);
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
        button(WorkoutStore.KIRILL, () -> dates(WorkoutStore.KIRILL));
        button(WorkoutStore.IGOR, () -> dates(WorkoutStore.IGOR));
        if (store.hasUnassigned()) secondary("Старые записи без пользователя", () -> dates(""));
        secondary("На главную", this::home);
    }

    private void dates(String athlete) {
        journalAthlete = athlete;
        begin(athlete.isEmpty() ? "Старые записи" : "Журнал · " + athlete, "dates");
        List<String> dates = store.dates(athlete);
        if (dates.isEmpty()) paragraph("Записей пока нет. Добавьте первое упражнение.");
        for (String date : dates) button(date, () -> entries(athlete, date));
        secondary("Выбрать другого", this::chooseAthleteForJournal);
        secondary("На главную", this::home);
    }

    private void entries(String athlete, String date) {
        journalAthlete = athlete;
        begin((athlete.isEmpty() ? "Старые записи" : athlete) + " · " + date, "entries");
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
                secondary("Назначить Кириллу", () -> assignLegacy(entry, WorkoutStore.KIRILL, date));
                secondary("Назначить Игорю", () -> assignLegacy(entry, WorkoutStore.IGOR, date));
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
        begin("Выгрузить записи в XLS", "export_scope");
        paragraph("Сначала выберите, чьи записи включить в файл.");
        button("Кирилл", () -> exportRange(WorkoutStore.KIRILL));
        button("Игорь", () -> exportRange(WorkoutStore.IGOR));
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
            String scope = athlete == null ? "all" : WorkoutStore.KIRILL.equals(athlete) ? "kirill" : "igor";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.ms-excel");
            intent.putExtra(Intent.EXTRA_TITLE, "training-" + scope + "-" + from + "-" + to + ".xls");
            startActivityForResult(intent, SAVE_XLS_REQUEST);
        });
        secondary("Назад", this::chooseExportScope);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (exportFromField != null && "export_range".equals(screen)) {
            exportFrom = exportFromField.getText().toString().trim();
            exportTo = exportToField.getText().toString().trim();
        }
        state.putString("export_athlete", exportAthlete);
        state.putString("export_from", exportFrom);
        state.putString("export_to", exportTo);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_XLS_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
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

    private void startLive() {
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
        begin("Live-тренировка · A", "live");
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
        else paragraph("Это последнее упражнение программы A.");
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
                .setMessage("Выполнено упражнений: " + count + ". Заполнить журнал сначала для Кирилла, затем для Игоря?")
                .setPositiveButton("Заполнить журналы", (dialog, which) -> startLiveJournal())
                .setNegativeButton("Продолжить", null)
                .setNeutralButton("Без записи", (dialog, which) -> home()).show();
    }

    private void startLiveJournal() {
        liveIndices = new ArrayList<>();
        for (int i = 0; i < completed.length; i++) if (completed[i]) liveIndices.add(i);
        liveAnswers = new String[liveIndices.size() * 2][3];
        for (String[] answer : liveAnswers) for (int i = 0; i < 3; i++) answer[i] = "";
        liveJournalDate = LocalDate.now().toString();
        liveJournalIndex = 0;
        liveJournal();
    }

    private void liveJournal() {
        begin("Журнал после тренировки", "live_log");
        int exercisesPerAthlete = liveIndices.size();
        int participant = liveJournalIndex / exercisesPerAthlete;
        String athlete = participant == 0 ? WorkoutStore.KIRILL : WorkoutStore.IGOR;
        ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(liveJournalIndex % exercisesPerAthlete));
        String[] answer = liveAnswers[liveJournalIndex];
        page.addView(text(athlete, 21, GREEN, true));
        gap(8);
        paragraph("Сегодня · " + liveJournalDate + " · упражнение " + (liveJournalIndex % exercisesPerAthlete + 1) + " из " + exercisesPerAthlete);
        page.addView(text(exercise.name, 22, INK, true));
        gap(8);
        paragraph("План недели " + week + ": " + exercise.weeks[week - 1]);
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
        String nextLabel = liveJournalIndex == liveAnswers.length - 1 ? "Сохранить оба журнала" :
                liveJournalIndex == exercisesPerAthlete - 1 ? "Перейти к Игорю" : "Следующее упражнение";
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
        if (validate) {
            if (!liveOwnWeight && !validWeight(weight)) { liveWeightField.setError("Введите кг или выберите «Свой вес»"); return false; }
            if (!validRating(rpe, false)) { liveRpeField.setError("Число от 0 до 10"); return false; }
            if (!validRating(pain, true)) { livePainField.setError("Целое число от 0 до 10"); return false; }
        }
        liveAnswers[liveJournalIndex][0] = liveOwnWeight || weight.isEmpty() ? weight : weight.replace(',', '.') + " кг";
        liveAnswers[liveJournalIndex][1] = rpe;
        liveAnswers[liveJournalIndex][2] = pain;
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
            String athlete = i < exercisesPerAthlete ? WorkoutStore.KIRILL : WorkoutStore.IGOR;
            ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(i % exercisesPerAthlete));
            String[] answer = liveAnswers[i];
            records.add(new WorkoutStore.NewEntry(athlete,
                    new String[]{liveJournalDate, "A", exercise.name, answer[0], exercise.weeks[week - 1], answer[1], answer[2], ""}));
        }
        try {
            store.addAll(records);
            begin("Журнал сохранён", "saved");
            paragraph("Добавлено по " + exercisesPerAthlete + " упражнений для Кирилла и Игоря · " + liveJournalDate);
            button("Журнал Кирилла", () -> entries(WorkoutStore.KIRILL, liveJournalDate));
            button("Журнал Игоря", () -> entries(WorkoutStore.IGOR, liveJournalDate));
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
