package com.jump.progression;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.VideoView;
import android.widget.TextView;
import java.io.InputStream;
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

    @Test public void liveWorkoutOpensTheMatchingOfflineVideo() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        ProgramA program = new ProgramA(activity);
        ActivityInfo videoScreen = activity.getPackageManager().getActivityInfo(
                new ComponentName(activity, VideoActivity.class), 0);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, videoScreen.screenOrientation);
        ActivityInfo workoutScreen = activity.getPackageManager().getActivityInfo(
                new ComponentName(activity, MainActivity.class), 0);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, workoutScreen.screenOrientation);
        String[] names = {
                "Мёртвый жук", "Ротация в блоке", "Snap Down", "Snap Down → Box Jump",
                "Болгарский сплит-присед", "Румынская тяга на одной ноге",
                "Зашагивание на тумбу + контролируемое опускание", "Жим гантелей лёжа",
                "Тяга гантели с упором на скамью", "Подъём на носок одной ногой"
        };
        assertEquals(names.length, program.exercises.size());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], program.exercises.get(i).name);
            try (InputStream video = activity.getResources().openRawResource(VideoActivity.videoForIndex(i))) {
                byte[] header = new byte[12];
                assertEquals(12, video.read(header));
                assertEquals("ftyp", new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
            }
        }
        click(activity, "Live-тренировка");
        click(activity, "Программа А");
        click(activity, "Посмотреть видео упражнения");
        Intent first = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(VideoActivity.class.getName(), first.getComponent().getClassName());
        assertEquals(0, first.getIntExtra(VideoActivity.EXTRA_EXERCISE_INDEX, -1));
        assertEquals(names[0], first.getStringExtra(VideoActivity.EXTRA_EXERCISE_NAME));
        for (int i = 1; i < names.length; i++) click(activity, "Следующее упражнение");
        click(activity, "Посмотреть видео упражнения");
        Intent last = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(9, last.getIntExtra(VideoActivity.EXTRA_EXERCISE_INDEX, -1));
        assertEquals(names[9], last.getStringExtra(VideoActivity.EXTRA_EXERCISE_NAME));

        VideoActivity player = Robolectric.buildActivity(VideoActivity.class, first).setup().get();
        assertNotNull(find(player.getWindow().getDecorView(), VideoView.class, null));
        assertNull(find(player.getWindow().getDecorView(), SeekBar.class, null));
        assertNull(find(player.getWindow().getDecorView(), Button.class, "−10 с"));
        assertNull(find(player.getWindow().getDecorView(), Button.class, "+10 с"));
        find(player.getWindow().getDecorView(), Button.class, "← К тренировке").performClick();
        assertTrue(player.isFinishing());
    }

    @Test public void manualEntryUsesOwnWeightAndNumericRepsAndCanBeDeleted() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        assertTrue(new WorkoutStore(activity).createProfile("Анна"));
        click(activity, "Создать запись тренировки");
        click(activity, "Анна");
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
        List<WorkoutStore.Entry> saved = new WorkoutStore(activity).onDate("Анна", "2026-10-07");
        assertEquals(1, saved.size());
        assertEquals("Анна", saved.get(0).athlete);
        assertEquals("Свой вес", saved.get(0).values[3]);
        click(activity, "Удалить запись · Мёртвый жук");
        assertEquals(1, new WorkoutStore(activity).onDate("Анна", "2026-10-07").size());
        confirm();
        assertTrue(new WorkoutStore(activity).onDate("Анна", "2026-10-07").isEmpty());
    }

    @Test public void liveWorkoutLogsOnlyCompletedExercisesWithPrefilledPlan() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        assertTrue(new WorkoutStore(activity).createProfile("Анна"));
        assertTrue(new WorkoutStore(activity).createProfile("Борис"));
        ProgramA program = new ProgramA(activity);
        assertEquals(10, program.exercises.size());
        assertEquals("Мёртвый жук", program.exercises.get(0).name);
        assertEquals("Подъём на носок одной ногой", program.exercises.get(9).name);
        assertEquals(45, program.exercises.get(0).restSeconds());
        click(activity, "Live-тренировка");
        click(activity, "Программа А");
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
        for (CheckBox participant : all(root(activity), CheckBox.class)) participant.setChecked(true);
        click(activity, "Заполнить журнал");
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
        click(activity, "Следующий участник: Борис");
        assertNotNull(find(root(activity), TextView.class, "Борис"));
        click(activity, "Свой вес");
        List<EditText> igorFirst = all(root(activity), EditText.class);
        igorFirst.get(1).setText("6");
        igorFirst.get(2).setText("0");
        click(activity, "Следующее упражнение");
        List<EditText> igorSecond = all(root(activity), EditText.class);
        igorSecond.get(0).setText("10");
        igorSecond.get(1).setText("9");
        igorSecond.get(2).setText("2");
        click(activity, "Сохранить журнал");
        String today = LocalDate.now().toString();
        WorkoutStore store = new WorkoutStore(activity);
        List<WorkoutStore.Entry> kirill = store.onDate("Анна", today);
        List<WorkoutStore.Entry> igor = store.onDate("Борис", today);
        assertEquals(2, kirill.size());
        assertEquals(2, igor.size());
        assertArrayEquals(new String[]{today, "A", "Мёртвый жук", "Свой вес", "3×8/сторона", "7", "0", ""}, kirill.get(0).values);
        assertArrayEquals(new String[]{today, "A", "Ротация в блоке", "8 кг", "3×8/сторона", "8", "1", ""}, kirill.get(1).values);
        assertArrayEquals(new String[]{today, "A", "Мёртвый жук", "Свой вес", "3×8/сторона", "6", "0", ""}, igor.get(0).values);
        assertArrayEquals(new String[]{today, "A", "Ротация в блоке", "10 кг", "3×8/сторона", "9", "2", ""}, igor.get(1).values);
        click(activity, "На главную");
        click(activity, "Журнал тренировок");
        click(activity, "Анна");
        click(activity, today);
        assertNotNull(find(root(activity), TextView.class, "Вес: 8 кг"));
        assertNull(find(root(activity), TextView.class, "Вес: 10 кг"));
    }

    @Test public void profileManagementAndProgramBUseMatchingVideos() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        click(activity, "Профили");
        all(root(activity), EditText.class).get(0).setText("  Мария  ");
        click(activity, "Создать профиль");
        assertEquals(java.util.Collections.singletonList("Мария"), new WorkoutStore(activity).profiles());
        click(activity, "На главную");
        ProgramA programB = new ProgramA(activity, "B");
        assertEquals(10, programB.exercises.size());
        assertEquals("Прыжки на одной ноге из стороны в сторону", programB.exercises.get(2).name);
        assertEquals("Подъём на носок одной ногой", programB.exercises.get(9).name);
        assertEquals(90, programB.exercises.get(3).restSeconds());
        for (int i = 0; i < 10; i++) {
            try (InputStream video = activity.getResources().openRawResource(VideoActivity.videoForIndex("B", i))) {
                byte[] header = new byte[12];
                assertEquals(12, video.read(header));
                assertEquals("ftyp", new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
            }
        }
        assertEquals(VideoActivity.videoForIndex(0), VideoActivity.videoForIndex("B", 0));
        assertEquals(VideoActivity.videoForIndex(9), VideoActivity.videoForIndex("B", 9));
        click(activity, "Live-тренировка");
        click(activity, "Программа Б");
        click(activity, "Следующее упражнение");
        click(activity, "Следующее упражнение");
        assertNotNull(find(root(activity), TextView.class, programB.exercises.get(2).name));
        click(activity, "Посмотреть видео упражнения");
        Intent video = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("B", video.getStringExtra(VideoActivity.EXTRA_PROGRAM));
        assertEquals(2, video.getIntExtra(VideoActivity.EXTRA_EXERCISE_INDEX, -1));
        click(activity, "Упражнение выполнено");
        click(activity, "Завершить тренировку");
        confirm();
        all(root(activity), CheckBox.class).get(0).setChecked(true);
        click(activity, "Заполнить журнал");
        assertNotNull(find(root(activity), TextView.class, programB.exercises.get(2).name));
        click(activity, "Свой вес");
        List<EditText> answers = all(root(activity), EditText.class);
        answers.get(1).setText("7");
        answers.get(2).setText("0");
        click(activity, "Сохранить журнал");
        String today = LocalDate.now().toString();
        List<WorkoutStore.Entry> saved = new WorkoutStore(activity).onDate("Мария", today);
        assertEquals(1, saved.size());
        assertEquals("B", saved.get(0).values[1]);
        assertEquals(programB.exercises.get(2).name, saved.get(0).values[2]);
        assertEquals(programB.exercises.get(2).weeks[0], saved.get(0).values[4]);
        click(activity, "На главную");
        click(activity, "Профили");
        click(activity, "Удалить профиль · Мария");
        confirm();
        assertTrue(new WorkoutStore(activity).profiles().isEmpty());
        assertEquals(1, new WorkoutStore(activity).onDate("", today).size());
    }

    @Test public void exportRequiresDateRangeAndOpensPhoneSavePicker() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.deleteDatabase("workouts.db");
        assertTrue(new WorkoutStore(activity).createProfile("Анна"));
        new WorkoutStore(activity).add("Анна",
                new String[]{"2026-01-02", "A", "Мёртвый жук", "Свой вес", "2×6", "7", "0", ""});
        click(activity, "Выгрузить XLS");
        click(activity, "Анна");
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
