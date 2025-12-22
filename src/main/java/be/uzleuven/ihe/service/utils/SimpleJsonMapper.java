package be.uzleuven.ihe.service.utils;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Simple JSON serializer/deserializer without external dependencies.
 * Handles basic types, Lists, and nested objects.
 */
public class SimpleJsonMapper {

    public static String toJson(Object obj) throws Exception {
        if (obj == null) {
            return "null";
        }

        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }

        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof List) {
            return listToJson((List<?>) obj);
        }

        if (obj instanceof Map) {
            return mapToJson((Map<?, ?>) obj);
        }

        // Handle regular objects via reflection
        return objectToJson(obj);
    }

    private static String listToJson(List<?> list) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String mapToJson(Map<?, ?> map) throws Exception {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
            sb.append(toJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String objectToJson(Object obj) throws Exception {
        StringBuilder sb = new StringBuilder("{");
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        boolean first = true;

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(obj);

            // Skip null values to keep JSON clean
            if (value == null) {
                continue;
            }

            // Skip empty lists
            if (value instanceof List && ((List<?>) value).isEmpty()) {
                continue;
            }

            if (!first) sb.append(",");
            first = false;

            sb.append("\"").append(field.getName()).append("\":");
            sb.append(toJson(value));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = 0; j < 4 - hex.length(); j++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        if (json == null || json.trim().isEmpty() || json.equals("null")) {
            return null;
        }

        json = json.trim();

        if (json.startsWith("{")) {
            return parseObject(json, clazz);
        } else if (json.startsWith("[")) {
            if (clazz == List.class) {
                return (T) parseList(json);
            }
        }

        throw new IllegalArgumentException("Cannot parse JSON: " + json);
    }

    private static <T> T parseObject(String json, Class<T> clazz) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();

        // Remove outer braces
        json = json.substring(1, json.length() - 1).trim();

        if (json.isEmpty()) {
            return instance;
        }

        Map<String, String> fields = parseFields(json);

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();

            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);

                Object value = parseValue(fieldValue, field.getType());
                field.set(instance, value);
            } catch (NoSuchFieldException e) {
                // Skip unknown fields
            }
        }

        return instance;
    }

    private static Map<String, String> parseFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int start = 0;
        String currentKey = null;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ':' && depth == 0 && currentKey == null) {
                currentKey = json.substring(start, i).trim();
                if (currentKey.startsWith("\"") && currentKey.endsWith("\"")) {
                    currentKey = currentKey.substring(1, currentKey.length() - 1);
                }
                start = i + 1;
            } else if (c == ',' && depth == 0) {
                String value = json.substring(start, i).trim();
                if (currentKey != null) {
                    fields.put(currentKey, value);
                }
                currentKey = null;
                start = i + 1;
            }
        }

        // Add last field
        if (currentKey != null) {
            String value = json.substring(start).trim();
            fields.put(currentKey, value);
        }

        return fields;
    }

    private static Object parseValue(String value, Class<?> targetType) throws Exception {
        value = value.trim();

        if (value.equals("null")) {
            return null;
        }

        if (targetType == String.class) {
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return unescape(value.substring(1, value.length() - 1));
            }
            return value;
        }

        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        }

        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }

        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        }

        if (List.class.isAssignableFrom(targetType)) {
            return parseList(value);
        }

        if (value.startsWith("{")) {
            return parseObject(value, targetType);
        }

        return null;
    }

    private static List<Object> parseList(String json) {
        List<Object> list = new ArrayList<>();

        json = json.substring(1, json.length() - 1).trim();

        if (json.isEmpty()) {
            return list;
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int start = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String item = json.substring(start, i).trim();
                if (!item.isEmpty()) {
                    list.add(parseSimpleValue(item));
                }
                start = i + 1;
            }
        }

        // Add last item
        String item = json.substring(start).trim();
        if (!item.isEmpty()) {
            list.add(parseSimpleValue(item));
        }

        return list;
    }

    private static Object parseSimpleValue(String value) {
        value = value.trim();

        if (value.equals("null")) {
            return null;
        }

        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unescape(value.substring(1, value.length() - 1));
        }

        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }

        if (value.startsWith("{") || value.startsWith("[")) {
            return value; // Return as string for nested structures
        }

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case 'b':
                        sb.append('\b');
                        i++;
                        break;
                    case 'f':
                        sb.append('\f');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        }
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

