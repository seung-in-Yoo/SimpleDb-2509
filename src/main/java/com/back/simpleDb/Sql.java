package com.back.simpleDb;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.lang.reflect.Field;

public class Sql {

    private final SimpleDb simpleDb;
    private final StringBuilder sb = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    public Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    public Sql append(String sqlPart, Object... values) {
        if (sqlPart == null || sqlPart.trim().isEmpty()) return this;
        if (sb.length() > 0) sb.append(" ");
        sb.append(sqlPart.trim());
        if (values != null && values.length > 0) {
            params.addAll(Arrays.asList(values));
        }
        return this;
    }

    public Sql appendIn(String sqlPart, Object... values) {
        Objects.requireNonNull(sqlPart, "sqlPart");
        List<Object> flat = flattenParams(values);

        if (flat.isEmpty()) {
            sb.append(sqlPart.replace("(?)", "(NULL)").replace("?", "NULL")).append(" ");
            return this;
        }

        String placeholders = String.join(", ", Collections.nCopies(flat.size(), "?"));

        if (sqlPart.contains("(?)")) {
            sqlPart = sqlPart.replace("(?)", "(" + placeholders + ")");
        } else {
            int q = sqlPart.indexOf('?');
            if (q < 0) throw new IllegalArgumentException("placeholder '?' 필요: " + sqlPart);
            sqlPart = sqlPart.substring(0, q) + placeholders + sqlPart.substring(q + 1);
        }

        sb.append(sqlPart).append(" ");
        params.addAll(flat);
        return this;
    }

    public static List<Object> flattenParams(Object... values) {
        List<Object> flat = new ArrayList<>();
        if (values == null) return flat;

        for (Object v : values) {
            if (v == null) {
                flat.add(null);
            } else if (v.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(v);
                for (int i = 0; i < len; i++) {
                    flat.add(java.lang.reflect.Array.get(v, i));
                }
            } else if (v instanceof Collection<?>) {
                flat.addAll((Collection<?>) v);
            } else {
                flat.add(v);
            }
        }
        return flat;
    }

    public long insert() {
        try (PreparedStatement ps = prepare(true)) {
            bindParams(ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int update() { return executeDml(); }
    public int delete() { return executeDml(); }

    private int executeDml() {
        try (PreparedStatement ps = prepare(false)) {
            bindParams(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> selectRows() {
        try (PreparedStatement ps = prepare(false)) {
            bindParams(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return toRowList(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> List<T> selectRows(Class<T> clazz) {
        List<Map<String, Object>> rows = selectRows();
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(mapToEntity(clazz, row));
        return result;
    }

    public <T> T selectRow(Class<T> clazz) {
        Map<String, Object> row = selectRow();
        return row == null ? null : mapToEntity(clazz, row);
    }

    public Long selectLong() {
        Object v = getSingleValue();
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    public List<Long> selectLongs() {
        List<Map<String, Object>> rows = selectRows();
        List<Long> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!row.isEmpty()) {
                Object v = row.values().iterator().next();
                if (v instanceof Number) result.add(((Number) v).longValue());
                else if (v != null) result.add(Long.parseLong(v.toString()));
            }
        }
        return result;
    }

    public String selectString() {
        Object v = getSingleValue();
        return v == null ? null : v.toString();
    }

    public Boolean selectBoolean() {
        Object v = getSingleValue();
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    public LocalDateTime selectDatetime() {
        Object v = getSingleValue();
        if (v == null) return null;
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime();
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof String) return LocalDateTime.parse(((String) v).replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        throw new RuntimeException("지원하지않는 datetime 타입: " + v.getClass() + ", value=" + v);
    }

    private Object getSingleValue() {
        Map<String, Object> row = selectRow();
        if (row == null || row.isEmpty()) return null;
        return row.values().iterator().next();
    }

    private PreparedStatement prepare(boolean returnKeys) throws SQLException {
        Connection conn = simpleDb.getConnection();
        simpleDb.log(toSql(), params.toArray());
        return returnKeys
                ? conn.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS)
                : conn.prepareStatement(sb.toString());
    }

    private void bindParams(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
    }

    private List<Map<String, Object>> toRowList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
            rows.add(row);
        }
        return rows;
    }

    private <T> T mapToEntity(Class<T> clazz, Map<String, Object> row) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                try {
                    Field f = clazz.getDeclaredField(e.getKey());
                    f.setAccessible(true);
                    f.set(obj, e.getValue());
                } catch (NoSuchFieldException ignore) {}
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String toSql() {
        return sb.toString();
    }
}