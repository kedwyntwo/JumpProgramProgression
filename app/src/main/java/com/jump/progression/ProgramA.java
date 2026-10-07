package com.jump.progression;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProgramA {
    static final class Exercise {
        final String block, name, rest, focus;
        final String[] weeks;

        Exercise(JSONObject data) throws JSONException {
            block = data.getString("block");
            name = data.getString("name");
            rest = data.getString("rest");
            focus = data.getString("focus");
            JSONArray source = data.getJSONArray("weeks");
            weeks = new String[4];
            for (int i = 0; i < 4; i++) weeks[i] = source.getString(i);
        }

        int restSeconds() {
            Matcher matcher = Pattern.compile("\\d+").matcher(rest);
            int seconds = 0;
            while (matcher.find()) seconds = Integer.parseInt(matcher.group());
            return seconds;
        }
    }

    final List<Exercise> exercises = new ArrayList<>();

    ProgramA(Context context) throws IOException, JSONException {
        try (InputStream stream = context.getAssets().open("program_a.json")) {
            byte[] bytes = new byte[stream.available()];
            int read = 0;
            while (read < bytes.length) {
                int count = stream.read(bytes, read, bytes.length - read);
                if (count < 0) throw new IOException("Unexpected end of program data");
                read += count;
            }
            JSONArray source = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            for (int i = 0; i < source.length(); i++) exercises.add(new Exercise(source.getJSONObject(i)));
        }
    }
}
