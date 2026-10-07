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
        assertTrue(store.onDate(WorkoutStore.KIRILL, "2026-01-01").isEmpty());
        long id = store.onDate("", "2026-01-01").get(0).id;
        assertTrue(store.assign(id, WorkoutStore.KIRILL));
        assertFalse(store.hasUnassigned());
        assertEquals(1, store.onDate(WorkoutStore.KIRILL, "2026-01-01").size());
    }

    @Test public void exportsRealXlsWithOneExercisePerRowAndInclusiveDateRange() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("workouts.db");
        WorkoutStore store = new WorkoutStore(context);
        store.add(WorkoutStore.KIRILL, row("2026-01-01", "До периода", "5 кг"));
        store.add(WorkoutStore.KIRILL, row("2026-01-02", "Мёртвый жук", "Свой вес"));
        store.add(WorkoutStore.IGOR, row("2026-01-02", "Ротация в блоке", "8 кг"));
        store.add(WorkoutStore.IGOR, row("2026-01-03", "После периода", "9 кг"));

        List<WorkoutStore.Entry> rows = store.exportRows(null, "2026-01-02", "2026-01-02");
        assertEquals(2, rows.size());
        assertEquals(1, store.exportRows(WorkoutStore.KIRILL, "2026-01-02", "2026-01-02").size());
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
            assertEquals("Кирилл", sheet.getCell(0, 1).getContents());
            assertEquals("Мёртвый жук", sheet.getCell(3, 1).getContents());
            assertEquals("Игорь", sheet.getCell(0, 2).getContents());
            assertEquals("Ротация в блоке", sheet.getCell(3, 2).getContents());
            assertEquals("8 кг", sheet.getCell(4, 2).getContents());
            book.close();
        }
    }
}
