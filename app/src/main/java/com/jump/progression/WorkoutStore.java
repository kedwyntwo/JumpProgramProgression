package com.jump.progression;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class WorkoutStore extends SQLiteOpenHelper {
    static final String KIRILL = "Кирилл";
    static final String IGOR = "Игорь";
    static final String[] FIELDS = {"date", "workout", "exercise", "weight", "sets_reps", "rpe", "pain", "comment"};

    static final class Entry {
        final long id;
        final String athlete;
        final String[] values;

        Entry(long id, String athlete, String[] values) {
            this.id = id;
            this.athlete = athlete;
            this.values = values;
        }
    }

    static final class NewEntry {
        final String athlete;
        final String[] values;

        NewEntry(String athlete, String[] values) {
            this.athlete = athlete;
            this.values = values;
        }
    }

    WorkoutStore(Context context) {
        super(context, "workouts.db", null, 2);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT, athlete TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX entries_by_date ON entries(date DESC, id)");
        db.execSQL("CREATE INDEX entries_by_athlete_date ON entries(athlete, date DESC, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN athlete TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX entries_by_athlete_date ON entries(athlete, date DESC, id)");
        }
    }

    private static void requireAthlete(String athlete) {
        if (!KIRILL.equals(athlete) && !IGOR.equals(athlete))
            throw new IllegalArgumentException("Unknown athlete");
    }

    void add(String athlete, String[] values) {
        requireAthlete(athlete);
        insert(getWritableDatabase(), new NewEntry(athlete, values));
    }

    void addAll(List<NewEntry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (NewEntry entry : entries) {
                requireAthlete(entry.athlete);
                insert(db, entry);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insert(SQLiteDatabase db, NewEntry entry) {
        ContentValues row = new ContentValues();
        row.put("athlete", entry.athlete);
        for (int i = 0; i < FIELDS.length; i++) row.put(FIELDS[i], entry.values[i].trim());
        db.insertOrThrow("entries", null, row);
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("entries", "id = ?", new String[]{Long.toString(id)}) == 1;
    }

    boolean assign(long id, String athlete) {
        requireAthlete(athlete);
        ContentValues values = new ContentValues();
        values.put("athlete", athlete);
        return getWritableDatabase().update("entries", values, "id = ? AND athlete = ''", new String[]{Long.toString(id)}) == 1;
    }

    boolean hasUnassigned() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM entries WHERE athlete = '' LIMIT 1", null)) {
            return cursor.moveToFirst();
        }
    }

    List<String> dates(String athlete) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT DISTINCT date FROM entries WHERE athlete = ? ORDER BY date DESC", new String[]{athlete})) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    List<Entry> onDate(String athlete, String date) {
        return query("athlete = ? AND date = ?", new String[]{athlete, date}, "id ASC");
    }

    List<Entry> exportRows(String athlete, String from, String to) {
        if (athlete == null)
            return query("date >= ? AND date <= ?", new String[]{from, to}, "date ASC, id ASC");
        return query("athlete = ? AND date >= ? AND date <= ?", new String[]{athlete, from, to}, "date ASC, id ASC");
    }

    private List<Entry> query(String selection, String[] args, String order) {
        List<Entry> result = new ArrayList<>();
        String[] columns = new String[FIELDS.length + 2];
        columns[0] = "id";
        columns[1] = "athlete";
        System.arraycopy(FIELDS, 0, columns, 2, FIELDS.length);
        try (Cursor cursor = getReadableDatabase().query("entries", columns, selection, args, null, null, order)) {
            while (cursor.moveToNext()) {
                String[] row = new String[FIELDS.length];
                for (int i = 0; i < row.length; i++) row[i] = cursor.getString(i + 2);
                result.add(new Entry(cursor.getLong(0), cursor.getString(1), row));
            }
        }
        return result;
    }
}
