package com.jump.progression;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class WorkoutStore extends SQLiteOpenHelper {
    static final String[] FIELDS = {"date", "workout", "exercise", "weight", "sets_reps", "rpe", "pain", "comment"};

    static final class Entry {
        final long id;
        final String[] values;

        Entry(long id, String[] values) {
            this.id = id;
            this.values = values;
        }
    }

    WorkoutStore(Context context) {
        super(context, "workouts.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT)");
        db.execSQL("CREATE INDEX entries_by_date ON entries(date DESC, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    void add(String[] values) {
        insert(getWritableDatabase(), values);
    }

    void addAll(List<String[]> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String[] values : entries) insert(db, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insert(SQLiteDatabase db, String[] values) {
        ContentValues row = new ContentValues();
        for (int i = 0; i < FIELDS.length; i++) row.put(FIELDS[i], values[i].trim());
        db.insertOrThrow("entries", null, row);
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("entries", "id = ?", new String[]{Long.toString(id)}) == 1;
    }

    List<String> dates() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT DISTINCT date FROM entries ORDER BY date DESC", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    List<Entry> onDate(String date) {
        List<Entry> result = new ArrayList<>();
        String[] columns = new String[FIELDS.length + 1];
        columns[0] = "id";
        System.arraycopy(FIELDS, 0, columns, 1, FIELDS.length);
        try (Cursor cursor = getReadableDatabase().query("entries", columns, "date = ?", new String[]{date}, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                String[] row = new String[FIELDS.length];
                for (int i = 0; i < row.length; i++) row[i] = cursor.getString(i + 1);
                result.add(new Entry(cursor.getLong(0), row));
            }
        }
        return result;
    }
}
