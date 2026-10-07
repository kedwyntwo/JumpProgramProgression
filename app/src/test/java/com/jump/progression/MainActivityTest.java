package com.jump.progression;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    private <T extends View> T find(View view, Class<T> kind, String value) {
        if (kind.isInstance(view)) {
            if (value == null || (view instanceof TextView && ((TextView) view).getText().toString().equals(value))) return kind.cast(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), kind, value);
                if (result != null) return result;
            }
        }
        return null;
    }

    private <T extends View> List<T> all(View view, Class<T> kind) {
        List<T> result = new ArrayList<>();
        if (kind.isInstance(view)) result.add(kind.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(all(group.getChildAt(i), kind));
        }
        return result;
    }

    private View root(MainActivity activity) { return activity.getWindow().getDecorView(); }

    private void click(MainActivity activity, String label) {
        Button button = find(root(activity), Button.class, label);
        assertNotNull(label, button);
        button.performClick();
    }

    private void typeAndNext(MainActivity activity, String value) {
        List<EditText> fields = all(root(activity), EditText.class);
        assertEquals(1, fields.size());
        fields.get(0).setText(value);
        click(activity, "Далее");
    }

    private void confirm() {
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void manualEntryUsesOwnWeightAndNumericRepsAndCanBeDeleted() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        click(activity, "Создать запись тренировки");
        click(activity, WorkoutStore.KIRILL);
        typeAndNext(activity, "2026-10-07");
        typeAndNext(activity, "A");
        typeAndNext(activity, "Мёртвый жук");
        click(activity, "Свой вес");
        List<EditText> reps = all(root(activity), EditText.class);
        assertEquals(2, reps.size());
        reps.get(0).setText("2");
        reps.get(1).setText("6");
        click(activity, "Далее");
        typeAndNext(activity, "7");
        typeAndNext(activity, "0");
        all(root(activity), EditText.class).get(0).setText("Техника чистая");
        click(activity, "Сохранить запись");
        click(activity, "Посмотреть записи за дату");
        assertNotNull(find(root(activity), TextView.class, "Вес: Свой вес"));
        assertNotNull(find(root(activity), TextView.class, "Подходы × повторы: 2×6"));
        assertNotNull(find(root(activity), TextView.class, "Комментарий: Техника чистая"));
        assertTrue(all(root(activity), EditText.class).isEmpty());
        List<WorkoutStore.Entry> saved = new WorkoutStore(activity).onDate(WorkoutStore.KIRILL, "2026-10-07");
        assertEquals(1, saved.size());
        assertEquals(WorkoutStore.KIRILL, saved.get(0).athlete);
        assertEquals("Свой вес", saved.get(0).values[3]);
        click(activity, "Удалить запись · Мёртвый жук");
        assertEquals(1, new WorkoutStore(activity).onDate(WorkoutStore.KIRILL, "2026-10-07").size());
        confirm();
        assertTrue(new WorkoutStore(activity).onDate(WorkoutStore.KIRILL, "2026-10-07").isEmpty());
    }

    @Test public void liveWorkoutLogsOnlyCompletedExercisesWithPrefilledPlan() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        ProgramA program = new ProgramA(activity);
        assertEquals(10, program.exercises.size());
        assertEquals("Мёртвый жук", program.exercises.get(0).name);
        assertEquals("Подъём на носок одной ногой", program.exercises.get(9).name);
        assertEquals(45, program.exercises.get(0).restSeconds());
        click(activity, "Live-тренировка");
        Button weekThree = findByDescription(root(activity), "Неделя 3");
        assertNotNull(weekThree);
        weekThree.performClick();
        assertNotNull(find(root(activity), TextView.class, "Неделя 3: 3×8/сторона"));
        click(activity, "Отдых · 30–45 сек");
        assertNotNull(find(root(activity), TextView.class, "Осталось 45 сек"));
        click(activity, "Упражнение выполнено");
        assertNotNull(find(root(activity), TextView.class, "Ротация в блоке"));
        click(activity, "Упражнение выполнено");
        click(activity, "Завершить тренировку");
        confirm();
        assertNotNull(find(root(activity), TextView.class, "План недели 3: 3×8/сторона"));
        click(activity, "Свой вес");
        List<EditText> first = all(root(activity), EditText.class);
        first.get(1).setText("7");
        first.get(2).setText("0");
        click(activity, "Следующее упражнение");
        List<EditText> second = all(root(activity), EditText.class);
        second.get(0).setText("8");
        second.get(1).setText("8");
        second.get(2).setText("1");
        click(activity, "Перейти к Игорю");
        assertNotNull(find(root(activity), TextView.class, WorkoutStore.IGOR));
        click(activity, "Свой вес");
        List<EditText> igorFirst = all(root(activity), EditText.class);
        igorFirst.get(1).setText("6");
        igorFirst.get(2).setText("0");
        click(activity, "Следующее упражнение");
        List<EditText> igorSecond = all(root(activity), EditText.class);
        igorSecond.get(0).setText("10");
        igorSecond.get(1).setText("9");
        igorSecond.get(2).setText("2");
        click(activity, "Сохранить оба журнала");
        String today = LocalDate.now().toString();
        WorkoutStore store = new WorkoutStore(activity);
        List<WorkoutStore.Entry> kirill = store.onDate(WorkoutStore.KIRILL, today);
        List<WorkoutStore.Entry> igor = store.onDate(WorkoutStore.IGOR, today);
        assertEquals(2, kirill.size());
        assertEquals(2, igor.size());
        assertArrayEquals(new String[]{today, "A", "Мёртвый жук", "Свой вес", "3×8/сторона", "7", "0", ""}, kirill.get(0).values);
        assertArrayEquals(new String[]{today, "A", "Ротация в блоке", "8 кг", "3×8/сторона", "8", "1", ""}, kirill.get(1).values);
        assertArrayEquals(new String[]{today, "A", "Мёртвый жук", "Свой вес", "3×8/сторона", "6", "0", ""}, igor.get(0).values);
        assertArrayEquals(new String[]{today, "A", "Ротация в блоке", "10 кг", "3×8/сторона", "9", "2", ""}, igor.get(1).values);
        click(activity, "На главную");
        click(activity, "Журнал тренировок");
        click(activity, WorkoutStore.KIRILL);
        click(activity, today);
        assertNotNull(find(root(activity), TextView.class, "Вес: 8 кг"));
        assertNull(find(root(activity), TextView.class, "Вес: 10 кг"));
    }

    @Test public void exportRequiresDateRangeAndOpensPhoneSavePicker() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        new WorkoutStore(activity).add(WorkoutStore.KIRILL,
                new String[]{"2026-01-02", "A", "Мёртвый жук", "Свой вес", "2×6", "7", "0", ""});
        click(activity, "Выгрузить XLS");
        click(activity, WorkoutStore.KIRILL);
        List<EditText> dates = all(root(activity), EditText.class);
        assertEquals(2, dates.size());
        dates.get(0).setText("2026-01-03");
        dates.get(1).setText("2026-01-02");
        click(activity, "Выбрать место сохранения");
        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        dates.get(0).setText("2026-01-02");
        click(activity, "Выбрать место сохранения");
        ShadowActivity.IntentForResult launched = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(launched);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.intent.getAction());
        assertEquals("application/vnd.ms-excel", launched.intent.getType());
        assertTrue(launched.intent.getStringExtra(Intent.EXTRA_TITLE).endsWith(".xls"));
    }

    private Button findByDescription(View view, String description) {
        if (view instanceof Button && description.contentEquals(view.getContentDescription())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button result = findByDescription(group.getChildAt(i), description);
                if (result != null) return result;
            }
        }
        return null;
    }
}
