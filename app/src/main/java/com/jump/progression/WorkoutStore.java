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
        final String athlete;
        final String[] values;
        final String videoUri;

        Entry(long id, String athlete, String[] values, String videoUri) {
            this.id = id;
            this.athlete = athlete;
            this.values = values;
            this.videoUri = videoUri;
        }
    }

    static final class NewEntry {
        final String athlete;
        final String[] values;
        final String videoUri;

        NewEntry(String athlete, String[] values) {
            this(athlete, values, "");
        }

        NewEntry(String athlete, String[] values, String videoUri) {
            this.athlete = athlete;
            this.values = values;
            this.videoUri = videoUri;
        }
    }

    WorkoutStore(Context context) {
        super(context, "workouts.db", null, 4);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, workout TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT, sets_reps TEXT, rpe TEXT, pain TEXT, comment TEXT, athlete TEXT NOT NULL DEFAULT '', video_uri TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX entries_by_date ON entries(date DESC, id)");
        db.execSQL("CREATE INDEX entries_by_athlete_date ON entries(athlete, date DESC, id)");
        db.execSQL("CREATE TABLE profiles (name TEXT PRIMARY KEY COLLATE NOCASE)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN athlete TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX entries_by_athlete_date ON entries(athlete, date DESC, id)");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE profiles (name TEXT PRIMARY KEY COLLATE NOCASE)");
            db.execSQL("INSERT OR IGNORE INTO profiles(name) SELECT DISTINCT athlete FROM entries WHERE athlete != ''");
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE entries ADD COLUMN video_uri TEXT NOT NULL DEFAULT ''");
    }

    List<String> profiles() {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT name FROM profiles ORDER BY name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) names.add(cursor.getString(0));
        }
        return names;
    }

    boolean createProfile(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 50) return false;
        for (String existing : profiles()) if (existing.equalsIgnoreCase(normalized)) return false;
        ContentValues values = new ContentValues();
        values.put("name", normalized);
        return getWritableDatabase().insertWithOnConflict("profiles", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    boolean deleteProfile(String name) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!hasProfile(db, name)) return false;
            ContentValues orphan = new ContentValues();
            orphan.put("athlete", "");
            db.update("entries", orphan, "athlete = ?", new String[]{name});
            db.delete("profiles", "name = ?", new String[]{name});
            db.setTransactionSuccessful();
            return true;
        } finally { db.endTransaction(); }
    }

    private static boolean hasProfile(SQLiteDatabase db, String name) {
        if (name == null || name.isEmpty()) return false;
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM profiles WHERE name = ? LIMIT 1", new String[]{name})) {
            return cursor.moveToFirst();
        }
    }

    private static void requireAthlete(SQLiteDatabase db, String athlete) {
        if (!hasProfile(db, athlete)) throw new IllegalArgumentException("Профиль не найден");
    }

    void add(String athlete, String[] values) {
        add(athlete, values, "");
    }

    void add(String athlete, String[] values, String videoUri) {
        SQLiteDatabase db = getWritableDatabase();
        requireAthlete(db, athlete);
        insert(db, new NewEntry(athlete, values, videoUri));
    }

    void addAll(List<NewEntry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (NewEntry entry : entries) {
                requireAthlete(db, entry.athlete);
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
        row.put("video_uri", entry.videoUri);
        for (int i = 0; i < FIELDS.length; i++) row.put(FIELDS[i], entry.values[i].trim());
        db.insertOrThrow("entries", null, row);
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("entries", "id = ?", new String[]{Long.toString(id)}) == 1;
    }

    boolean assign(long id, String athlete) {
        requireAthlete(getWritableDatabase(), athlete);
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
        String[] columns = new String[FIELDS.length + 3];
        columns[0] = "id";
        columns[1] = "athlete";
        System.arraycopy(FIELDS, 0, columns, 2, FIELDS.length);
        columns[columns.length - 1] = "video_uri";
        try (Cursor cursor = getReadableDatabase().query("entries", columns, selection, args, null, null, order)) {
            while (cursor.moveToNext()) {
                String[] row = new String[FIELDS.length];
                for (int i = 0; i < row.length; i++) row[i] = cursor.getString(i + 2);
                result.add(new Entry(cursor.getLong(0), cursor.getString(1), row,
                        cursor.getString(columns.length - 1)));
            }
        }
        return result;
    }
}
