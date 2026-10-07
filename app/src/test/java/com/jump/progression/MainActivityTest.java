package com.jump.progression;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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

    private void click(MainActivity activity, String label) {
        Button button = find(activity.getWindow().getDecorView(), Button.class, label);
        assertNotNull(label, button);
        button.performClick();
    }

    @Test public void savesEightFieldsAndShowsReadOnlyDateView() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        click(activity, "Создать запись тренировки");
        String[] values = {"2026-10-07", "A", "Мёртвый жук", "8 кг", "2×8/сторона", "7", "0", "Техника чистая"};
        for (int i = 0; i < values.length; i++) {
            EditText field = find(activity.getWindow().getDecorView(), EditText.class, null);
            assertNotNull(field);
            field.setText(values[i]);
            click(activity, i == values.length - 1 ? "Сохранить запись" : "Далее");
        }
        click(activity, "Посмотреть записи за дату");
        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, TextView.class, "Вес: 8 кг"));
        assertNotNull(find(root, TextView.class, "Подходы × повторы: 2×8/сторона"));
        assertNotNull(find(root, TextView.class, "RPE: 7"));
        assertNotNull(find(root, TextView.class, "Боль / дискомфорт 0–10: 0"));
        assertNotNull(find(root, TextView.class, "Комментарий: Техника чистая"));
        assertNull(find(root, EditText.class, null));
        List<String[]> saved = new WorkoutStore(activity).onDate("2026-10-07");
        assertEquals(1, saved.size());
        assertArrayEquals(values, saved.get(0));
    }

    @Test public void liveWorkoutUsesSpreadsheetOrderAndWeekPlan() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        ProgramA program = new ProgramA(activity);
        assertEquals(10, program.exercises.size());
        assertEquals("Мёртвый жук", program.exercises.get(0).name);
        assertEquals("Подъём на носок одной ногой", program.exercises.get(9).name);
        assertEquals(45, program.exercises.get(0).restSeconds());
        assertEquals(90, program.exercises.get(3).restSeconds());
        click(activity, "Live-тренировка");
        Button weekThree = findByDescription(activity.getWindow().getDecorView(), "Неделя 3");
        assertNotNull(weekThree);
        weekThree.performClick();
        assertNotNull(find(activity.getWindow().getDecorView(), TextView.class, "Неделя 3: 3×8/сторона"));
        click(activity, "Отдых · 30–45 сек");
        assertNotNull(find(activity.getWindow().getDecorView(), TextView.class, "Осталось 45 сек"));
        click(activity, "Следующее упражнение");
        assertNotNull(find(activity.getWindow().getDecorView(), TextView.class, "Ротация в блоке"));
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
