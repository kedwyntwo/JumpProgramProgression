package com.jump.progression;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

final class XlsExporter {
    private static final String[] HEADERS = {"Пользователь", "Дата", "Тренировка", "Упражнение", "Вес", "Подходы × повторы", "RPE", "Боль / дискомфорт 0–10", "Комментарий"};

    static void write(OutputStream output, List<WorkoutStore.Entry> rows) throws IOException {
        if (rows.size() > 65535) throw new IOException("Формат XLS вмещает не более 65 535 упражнений в одном файле");
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(output);
            WritableSheet sheet = workbook.createSheet("Тренировки", 0);
            for (int column = 0; column < HEADERS.length; column++) {
                sheet.addCell(new Label(column, 0, HEADERS[column]));
                sheet.setColumnView(column, column == 8 ? 36 : 20);
            }
            for (int index = 0; index < rows.size(); index++) {
                WorkoutStore.Entry entry = rows.get(index);
                String athlete = entry.athlete.isEmpty() ? "Без пользователя" : entry.athlete;
                sheet.addCell(new Label(0, index + 1, athlete));
                for (int column = 0; column < entry.values.length; column++) {
                    String value = entry.values[column];
                    if (value.length() > 32767) throw new IOException("Слишком длинное значение для XLS в строке " + (index + 2));
                    sheet.addCell(new Label(column + 1, index + 1, value));
                }
            }
            workbook.write();
        } catch (WriteException error) {
            throw new IOException("Не удалось создать XLS", error);
        } finally {
            if (workbook != null) {
                try { workbook.close(); }
                catch (WriteException error) { throw new IOException("Не удалось завершить XLS", error); }
            }
        }
    }
}
