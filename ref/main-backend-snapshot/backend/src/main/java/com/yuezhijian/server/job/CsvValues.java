package com.yuezhijian.server.job;

final class CsvValues {
    private CsvValues() {
    }

    static String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && isFormulaPrefix(text.charAt(0))) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean isFormulaPrefix(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
