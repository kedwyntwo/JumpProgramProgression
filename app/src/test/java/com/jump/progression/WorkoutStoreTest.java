package com.jump.progression;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import jxl.Sheet;
import jxl.Workbook;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WorkoutStoreTest {
    private String[] row(String date, String exercise, String weight) {
        return new String[]{date, "A", exercise, weight, "2×6", "7", "0", ""};
    }

    @Test public void upgradesExistingDatabaseWithoutAttributingOldEntries() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("workouts.db");
        SQLiteDatabase old = context.openOrCreateDatabase("workouts.db", Context.MODE_PRIVATE, null);
        old.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT)");
        old.execSQL("INSERT INTO entries(date, workout, exercise, weight, sets_reps, rpe, pain, comment) VALUES ('2026-01-01', 'A', 'Мёртвый жук', 'Свой вес', '2×6', '7', '0', '')");
        old.setVersion(1);
        old.close();

        WorkoutStore store = new WorkoutStore(context);
        assertTrue(store.hasUnassigned());
        assertEquals(1, store.onDate("", "2026-01-01").size());
        assertTrue(store.onDate("Анна", "2026-01-01").isEmpty());
        long id = store.onDate("", "2026-01-01").get(0).id;
        assertTrue(store.createProfile("Анна"));
        assertTrue(store.assign(id, "Анна"));
        assertFalse(store.hasUnassigned());
        assertEquals(1, store.onDate("Анна", "2026-01-01").size());
    }

    @Test public void migratesExistingProfilesAndPreservesEntriesOnDeletion() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("workouts.db");
        SQLiteDatabase old = context.openOrCreateDatabase("workouts.db", Context.MODE_PRIVATE, null);
        old.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT, athlete TEXT NOT NULL DEFAULT '')");
        old.execSQL("INSERT INTO entries(date, workout, exercise, athlete) VALUES ('2026-01-03', 'A', 'Мёртвый жук', 'Кирилл')");
        old.execSQL("CREATE INDEX entries_by_date ON entries(date DESC, id)");
        old.execSQL("CREATE INDEX entries_by_athlete_date ON entries(athlete, date DESC, id)");
        old.setVersion(2);
        old.close();
        WorkoutStore store = new WorkoutStore(context);
        assertEquals(java.util.Collections.singletonList("Кирилл"), store.profiles());
        assertEquals("", store.onDate("Кирилл", "2026-01-03").get(0).videoUri);
        assertFalse(store.createProfile("кирилл"));
        assertTrue(store.deleteProfile("Кирилл"));
        assertTrue(store.profiles().isEmpty());
        assertEquals(1, store.onDate("", "2026-01-03").size());
    }

    @Test public void exportsRealXlsWithOneExercisePerRowAndInclusiveDateRange() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("workouts.db");
        WorkoutStore store = new WorkoutStore(context);
        assertTrue(store.createProfile("Анна"));
        assertTrue(store.createProfile("Борис"));
        store.add("Анна", row("2026-01-01", "До периода", "5 кг"));
        store.add("Анна", row("2026-01-02", "Мёртвый жук", "Свой вес"), "content://videos/attempt-1");
        assertEquals("content://videos/attempt-1", store.onDate("Анна", "2026-01-02").get(0).videoUri);
        store.add("Борис", row("2026-01-02", "Ротация в блоке", "8 кг"));
        store.add("Борис", row("2026-01-03", "После периода", "9 кг"));

        List<WorkoutStore.Entry> rows = store.exportRows(null, "2026-01-02", "2026-01-02");
        assertEquals(2, rows.size());
        assertEquals(1, store.exportRows("Анна", "2026-01-02", "2026-01-02").size());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XlsExporter.write(output, rows);
        byte[] bytes = output.toByteArray();
        assertTrue(bytes.length > 1000);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Workbook book = Workbook.getWorkbook(input);
            Sheet sheet = book.getSheet(0);
            assertEquals(3, sheet.getRows());
            assertEquals("Пользователь", sheet.getCell(0, 0).getContents());
            assertEquals("Подходы × повторы", sheet.getCell(5, 0).getContents());
            assertEquals("Анна", sheet.getCell(0, 1).getContents());
            assertEquals("Мёртвый жук", sheet.getCell(3, 1).getContents());
            assertEquals("Борис", sheet.getCell(0, 2).getContents());
            assertEquals("Ротация в блоке", sheet.getCell(3, 2).getContents());
            assertEquals("8 кг", sheet.getCell(4, 2).getContents());
            book.close();
        }
    }
}
