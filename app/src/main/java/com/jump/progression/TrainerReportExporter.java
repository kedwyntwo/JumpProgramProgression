package com.jump.progression;

import android.content.Context;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.Deflater;

final class TrainerReportExporter {
    private static final String[] LABELS = {
            "Дата", "Программа", "Упражнение", "Вес", "Подходы × повторы", "RPE", "Боль / дискомфорт", "Комментарий"
    };

    static void write(Context context, OutputStream output, List<WorkoutStore.Entry> rows) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            StringBuilder html = new StringBuilder();
            html.append("<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                    .append("<title>Отчёт о тренировках</title><style>body{font:16px system-ui,sans-serif;max-width:850px;margin:auto;padding:20px;color:#203330}")
                    .append("article{border:1px solid #b7cbc4;border-radius:12px;padding:16px;margin:18px 0}dt{font-weight:700;margin-top:8px}")
                    .append("dd{margin:2px 0 8px}video{width:100%;max-height:480px}a{color:#147d6c}</style><h1>Отчёт о тренировках</h1>")
                    .append("<p>Для просмотра видео распакуйте ZIP целиком и откройте этот файл.</p>");
            for (WorkoutStore.Entry row : rows) {
                html.append("<article><h2>").append(escape(row.values[2])).append("</h2><dl><dt>Пользователь</dt><dd>")
                        .append(escape(row.athlete.isEmpty() ? "Без профиля" : row.athlete)).append("</dd>");
                for (int i = 0; i < LABELS.length; i++) {
                    String value = row.values[i];
                    html.append("<dt>").append(LABELS[i]).append("</dt><dd>")
                            .append(escape(value == null || value.isEmpty() ? "—" : value)).append("</dd>");
                }
                html.append("</dl>");
                if (!row.videoUri.isEmpty()) {
                    String path = videoPath(context, row);
                    html.append("<h3>Видео исполнения</h3><video controls preload=\"none\" src=\"")
                            .append(path).append("\"></video><p><a href=\"").append(path)
                            .append("\">Открыть видео отдельно</a></p>");
                }
                html.append("</article>");
            }
            html.append("</html>");
            zip.putNextEntry(new ZipEntry("index.html"));
            zip.write(html.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            for (WorkoutStore.Entry row : rows) {
                if (row.videoUri.isEmpty()) continue;
                zip.putNextEntry(new ZipEntry(videoPath(context, row)));
                try (InputStream video = context.getContentResolver().openInputStream(Uri.parse(row.videoUri))) {
                    if (video == null) throw new IOException("Видео для записи " + row.id + " недоступно");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = video.read(buffer)) != -1) zip.write(buffer, 0, count);
                } catch (IOException | SecurityException error) {
                    throw new IOException("Видео для записи «" + row.values[2] + "» недоступно. Проверьте исходный файл.", error);
                }
                zip.closeEntry();
            }
        }
    }

    private static String videoPath(Context context, WorkoutStore.Entry row) {
        String mime;
        try { mime = context.getContentResolver().getType(Uri.parse(row.videoUri)); }
        catch (SecurityException error) { mime = null; }
        String extension = "video/webm".equalsIgnoreCase(mime) ? "webm" :
                "video/quicktime".equalsIgnoreCase(mime) ? "mov" :
                "video/3gpp".equalsIgnoreCase(mime) ? "3gp" :
                "video/x-matroska".equalsIgnoreCase(mime) ? "mkv" :
                "video/x-msvideo".equalsIgnoreCase(mime) ? "avi" : "mp4";
        return "videos/entry-" + row.id + "." + extension;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
