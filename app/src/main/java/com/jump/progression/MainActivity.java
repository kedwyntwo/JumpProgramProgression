package com.jump.progression;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import java.util.List;
import org.json.JSONException;

public final class MainActivity extends Activity {
    private static final String[] LABELS = {"Дата", "Тренировка", "Упражнение", "Вес", "Подходы × повторы", "RPE", "Боль / дискомфорт 0–10", "Комментарий"};
    private static final String[] HINTS = {"ГГГГ-ММ-ДД", "Например, A", "Название упражнения", "кг или собственный вес", "Например, 3×8", "0–10", "0–10", "Необязательно"};
    private static final int INK = Color.rgb(29, 48, 48);
    private static final int GREEN = Color.rgb(20, 125, 108);
    private static final int MUTED = Color.rgb(91, 110, 108);
    private WorkoutStore store;
    private ProgramA program;
    private LinearLayout page;
    private String screen = "home";
    private String[] draft = new String[8];
    private int step = 0, week = 1, currentExercise = 0;
    private EditText field;
    private CountDownTimer timer;
    private TextView timerLabel;
    private int remainingSeconds;

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

    private void secondary(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setTextColor(INK);
        view.setOnClickListener(v -> action.run());
        page.addView(view, new LinearLayout.LayoutParams(-1, dp(48)));
        gap(7);
    }

    private void home() {
        stopTimer();
        begin("Прогресс тренировок", "home");
        paragraph("Программа A и журнал тренировок всегда под рукой.");
        button("Создать запись тренировки", () -> newEntry(null));
        button("Все записи", this::dates);
        button("Live-тренировка", this::live);
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
        field = new EditText(this);
        field.setSingleLine(step != 7);
        field.setHint(HINTS[step]);
        field.setText(draft[step]);
        field.setTextSize(20);
        field.setInputType(step == 7 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
        if (step == 5 || step == 6) field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        page.addView(field, new LinearLayout.LayoutParams(-1, step == 7 ? dp(110) : dp(60)));
        gap(16);
        if (step == 0) secondary("Выбрать дату", this::pickDate);
        if (step == 2) {
            paragraph("Упражнение можно выбрать из программы A или ввести своё.");
            for (ProgramA.Exercise exercise : program.exercises) secondary(exercise.name, () -> field.setText(exercise.name));
        }
        button(step == LABELS.length - 1 ? "Сохранить запись" : "Далее", () -> {
            draft[step] = field.getText().toString().trim();
            if (!validStep()) return;
            if (step == LABELS.length - 1) save(); else { step++; wizard(); }
        });
        if (step > 0) secondary("Назад", () -> { draft[step] = field.getText().toString().trim(); step--; wizard(); });
        secondary("На главную", this::home);
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
        if ((step == 5 || step == 6) && !value.isEmpty()) {
            try {
                double number = Double.parseDouble(value.replace(',', '.'));
                if (number < 0 || number > 10 || (step == 6 && number != Math.floor(number))) throw new NumberFormatException();
            } catch (NumberFormatException error) { field.setError("Введите число от 0 до 10" + (step == 6 ? " без дробной части" : "")); return false; }
        }
        return true;
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
        List<String[]> rows = store.onDate(date);
        for (String[] row : rows) {
            TextView title = text(row[2], 20, INK, true);
            page.addView(title);
            gap(8);
            for (int i = 0; i < row.length; i++) {
                TextView value = text(LABELS[i] + ": " + (row[i].isEmpty() ? "—" : row[i]), 15, MUTED, false);
                page.addView(value);
                gap(4);
            }
            gap(20);
        }
        secondary("Все даты", this::dates);
        secondary("На главную", this::home);
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
            choice.setOnClickListener(v -> { week = number; currentExercise = 0; live(); });
            weeks.addView(choice, new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        page.addView(weeks);
        gap(18);
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
        if (currentExercise > 0) secondary("Предыдущее упражнение", () -> { currentExercise--; live(); });
        if (currentExercise < program.exercises.size() - 1) button("Следующее упражнение", () -> { currentExercise++; live(); });
        else paragraph("Это последнее упражнение программы A.");
        gap(10);
        page.addView(text("Порядок упражнений", 19, INK, true));
        gap(10);
        for (int i = 0; i < program.exercises.size(); i++) {
            ProgramA.Exercise item = program.exercises.get(i);
            final int index = i;
            secondary((i == currentExercise ? "● " : "") + (i + 1) + ". " + item.name + " · " + item.weeks[week - 1],
                    () -> { currentExercise = index; live(); });
        }
        secondary("На главную", this::home);
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
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 700);
                timerLabel.postDelayed(tone::release, 1000);
            }
        }.start();
    }

    private void stopTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    @Override public void onBackPressed() {
        if ("entries".equals(screen)) dates();
        else if ("wizard".equals(screen) && step > 0) { draft[step] = field.getText().toString().trim(); step--; wizard(); }
        else if ("home".equals(screen)) super.onBackPressed();
        else home();
    }

    @Override protected void onDestroy() {
        stopTimer();
        store.close();
        super.onDestroy();
    }
}
