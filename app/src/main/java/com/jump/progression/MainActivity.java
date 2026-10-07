package com.jump.progression;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public final class MainActivity extends Activity {
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
        button("Создать запись тренировки", () -> newEntry(null));
        button("Все записи", this::dates);
        button("Live-тренировка", this::startLive);
        gap(12);
        paragraph("Записи хранятся только на этом устройстве.");
    }

    private void newEntry(String[] previous) {
        draft = new String[8];
        draft[0] = previous == null ? LocalDate.now().toString() : previous[0];
        draft[1] = previous == null ? "A" : previous[1];
        draft[2] = previous == null ? program.exercises.get(0).name : "";
        for (int i = 3; i < draft.length; i++) draft[i] = "";
        step = 0;
        wizard();
    }

    private void wizard() {
        begin("Новая запись", "wizard");
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
        Calendar today = Calendar.getInstance();
        try {
            LocalDate date = LocalDate.parse(field.getText().toString().trim());
            today.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        } catch (DateTimeParseException ignored) { }
        new DatePickerDialog(this, (picker, year, month, day) ->
                field.setText(LocalDate.of(year, month + 1, day).toString()),
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
            store.add(draft);
            String[] saved = draft.clone();
            begin("Запись сохранена", "saved");
            paragraph(saved[0] + " · " + saved[2]);
            button("Добавить ещё упражнение", () -> newEntry(saved));
            button("Посмотреть записи за дату", () -> entries(saved[0]));
            secondary("На главную", this::home);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить запись: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void dates() {
        begin("Все записи", "dates");
        List<String> dates = store.dates();
        if (dates.isEmpty()) paragraph("Записей пока нет. Добавьте первое упражнение.");
        for (String date : dates) button(date, () -> entries(date));
        secondary("На главную", this::home);
    }

    private void entries(String date) {
        begin("Тренировка · " + date, "entries");
        List<WorkoutStore.Entry> rows = store.onDate(date);
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
                            if (store.onDate(date).isEmpty()) dates(); else entries(date);
                        } else Toast.makeText(this, "Запись не найдена", Toast.LENGTH_SHORT).show();
                    }).show());
            gap(20);
        }
        secondary("Все даты", this::dates);
        secondary("На главную", this::home);
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
                .setMessage("Выполнено упражнений: " + count + ". Добавить их в журнал за сегодня?")
                .setPositiveButton("Заполнить журнал", (dialog, which) -> startLiveJournal())
                .setNegativeButton("Продолжить", null)
                .setNeutralButton("Без записи", (dialog, which) -> home()).show();
    }

    private void startLiveJournal() {
        liveIndices = new ArrayList<>();
        for (int i = 0; i < completed.length; i++) if (completed[i]) liveIndices.add(i);
        liveAnswers = new String[liveIndices.size()][3];
        for (String[] answer : liveAnswers) for (int i = 0; i < 3; i++) answer[i] = "";
        liveJournalDate = LocalDate.now().toString();
        liveJournalIndex = 0;
        liveJournal();
    }

    private void liveJournal() {
        begin("Журнал после тренировки", "live_log");
        ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(liveJournalIndex));
        String[] answer = liveAnswers[liveJournalIndex];
        paragraph("Сегодня · " + liveJournalDate + " · упражнение " + (liveJournalIndex + 1) + " из " + liveIndices.size());
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
        button(liveJournalIndex == liveIndices.size() - 1 ? "Сохранить журнал" : "Следующее упражнение", () -> {
            if (!captureLiveAnswer(true)) return;
            if (liveJournalIndex == liveIndices.size() - 1) saveLiveJournal();
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
        List<String[]> records = new ArrayList<>();
        for (int i = 0; i < liveIndices.size(); i++) {
            ProgramA.Exercise exercise = program.exercises.get(liveIndices.get(i));
            String[] answer = liveAnswers[i];
            records.add(new String[]{liveJournalDate, "A", exercise.name, answer[0], exercise.weeks[week - 1], answer[1], answer[2], ""});
        }
        try {
            store.addAll(records);
            begin("Журнал сохранён", "saved");
            paragraph("Добавлено записей: " + records.size() + " · " + liveJournalDate);
            button("Посмотреть записи", () -> entries(liveJournalDate));
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
        if ("entries".equals(screen)) dates();
        else if ("wizard".equals(screen) && step > 0) { captureWizardStep(); step--; wizard(); }
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
