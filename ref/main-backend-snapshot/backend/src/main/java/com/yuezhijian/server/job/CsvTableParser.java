package com.yuezhijian.server.job;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class CsvTableParser {
    private CsvTableParser() {
    }

    static List<List<String>> parse(byte[] content, int maxRows) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        boolean rowHasContent = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(current);
                }
                rowHasContent = true;
                continue;
            }
            if (quoteClosed && current != ',' && current != '\r' && current != '\n') {
                throw new IllegalArgumentException("CSV引号后的内容无效");
            }
            if (current == '"') {
                if (field.length() != 0) throw new IllegalArgumentException("CSV引号必须位于字段开头");
                quoted = true;
                rowHasContent = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
                rowHasContent = true;
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
                if (rowHasContent || row.stream().anyMatch(value -> !value.isBlank())) add(rows, row, maxRows);
                row = new ArrayList<>();
                rowHasContent = false;
            } else {
                field.append(current);
                rowHasContent = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV存在未闭合的引号");
        if (rowHasContent || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            add(rows, row, maxRows);
        }
        return List.copyOf(rows);
    }

    private static void add(List<List<String>> rows, List<String> row, int maxRows) {
        if (rows.size() >= maxRows) throw new IllegalArgumentException("CSV数据行不能超过" + (maxRows - 1) + "行");
        rows.add(List.copyOf(row));
    }
}
