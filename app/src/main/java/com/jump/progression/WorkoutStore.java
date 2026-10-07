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

    WorkoutStore(Context context) {
        super(context, "workouts.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT)");
        db.execSQL("CREATE INDEX entries_by_date ON entries(date DESC, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    void add(String[] values) {
        ContentValues row = new ContentValues();
        for (int i = 0; i < FIELDS.length; i++) row.put(FIELDS[i], values[i].trim());
        getWritableDatabase().insertOrThrow("entries", null, row);
    }

    List<String> dates() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT DISTINCT date FROM entries ORDER BY date DESC", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    List<String[]> onDate(String date) {
        List<String[]> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("entries", FIELDS, "date = ?", new String[]{date}, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                String[] row = new String[FIELDS.length];
                for (int i = 0; i < row.length; i++) row[i] = cursor.getString(i);
                result.add(row);
            }
        }
        return result;
    }
}
