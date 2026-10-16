package com.jump.progression;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrainerReportExporterTest {
    @Test public void packagesResultsAndVideoWithoutLosingTheirBytes() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File clip = File.createTempFile("attempt-", ".mp4", context.getCacheDir());
        byte[] video = new byte[]{0, 1, 2, 3, 4, 5};
        Files.write(clip.toPath(), video);
        WorkoutStore.Entry entry = new WorkoutStore.Entry(42, "Анна & тренер",
                new String[]{"2026-10-16", "B", "Присед <вниз>", "12 кг", "2×6", "8", "1", "Без боли & ошибок"},
                Uri.fromFile(clip).toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrainerReportExporter.write(context, output, Arrays.asList(entry));
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry item;
            while ((item = zip.getNextEntry()) != null) files.put(item.getName(), zip.readAllBytes());
        }
        assertArrayEquals(video, files.get("videos/entry-42.mp4"));
        String html = new String(files.get("index.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("Анна &amp; тренер"));
        assertTrue(html.contains("Присед &lt;вниз&gt;"));
        assertTrue(html.contains("2×6"));
        assertTrue(html.contains("12 кг"));
        assertTrue(html.contains("Без боли &amp; ошибок"));
        assertTrue(html.contains("videos/entry-42.mp4"));
        assertFalse(html.contains("file://"));
    }
}
