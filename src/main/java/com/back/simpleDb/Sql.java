package com.back.simpleDb;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sql 클래스
 * - SimpleDb를 통해 SQL을 안전하게 실행하고 결과를 쉽게 다룰 수 있게 도와주는 클래스
 * - SQL 쿼리 문자열을 조합하고, 파라미터를 바인딩하며, 결과를 다양한 형태(List, Map, 객체)로 반환 가능
 */
public class Sql {
    private final SimpleDb simpleDb;  // Sql 객체가 소속될 SimpleDb 인스턴스
    private final StringBuilder sb = new StringBuilder(); // SQL 문자열을 점진적으로 조합
    private final List<Object> params = new ArrayList<>(); // SQL 파라미터 바인딩 리스트

    // 생성자 (패키지 내에서만 접근 가능)
    Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    /**
     * SQL 문자열을 이어 붙이고 파라미터를 추가
     * @param sqlPart SQL 문자열 일부
     * @param params SQL 파라미터
     */
    public Sql append(String sqlPart, Object... params) {
        if (sb.length() > 0) sb.append(" "); // 이전 문자열과 공백으로 구분
        sb.append(sqlPart);
        if (params != null && params.length > 0) this.params.addAll(Arrays.asList(params)); // 파라미터 누적
        return this;
    }

    /**
     * "IN" 구문용 메서드
     * "?" 하나를 n개의 "?, ?, ?"로 바꾸고, 파라미터를 추가
     * @param sqlPart "id IN (?)" 같은 SQL
     * @param inParams IN 절 값들
     */
    public Sql appendIn(String sqlPart, Object... inParams) {
        if (inParams == null || inParams.length == 0) {
            return append(sqlPart);
        }
        String placeholders = String.join(", ", Collections.nCopies(inParams.length, "?")); // ?, ?, ?
        String replaced = sqlPart.replaceFirst("\\?", placeholders); // 첫번째 ?만 치환
        return append(replaced, inParams);
    }

    /**
     * PreparedStatement 생성 및 파라미터 바인딩
     */
    private PreparedStatement prepare() throws SQLException {
        return simpleDb.prepare(sb.toString(), params.toArray());
    }

    /**
     * INSERT 수행 후 자동 생성된 키 반환
     */
    public long insert() {
        try (PreparedStatement ps = prepare()) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            return 0L;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * UPDATE 수행
     */
    public int update() {
        try (PreparedStatement ps = prepare()) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * DELETE 수행 (UPDATE와 동일)
     */
    public int delete() {
        return update();
    }

    /**
     * SELECT 결과를 List<Map<String,Object>>로 반환
     * - 컬럼 이름 -> Map key
     * - 컬럼 값 -> Map value
     */
    public List<Map<String, Object>> selectRows() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);

                    // 타입 변환: SQL -> Java
                    if (val instanceof Timestamp ts) val = ts.toLocalDateTime();
                    else if (val instanceof java.sql.Date d) val = d.toLocalDate();
                    else if (val instanceof byte[] bytes) val = bytes.length > 0 ? bytes[0] != 0 : false;

                    // 숫자는 그대로
                    String label = meta.getColumnLabel(i);
                    row.put(label, val);
                }
                list.add(row);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();
        return rows.isEmpty() ? null : rows.get(0);
    }

    // 단일 컬럼 결과를 LocalDateTime으로 반환
    public LocalDateTime selectDatetime() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toLocalDateTime();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Long selectLong() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Long> selectLongs() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            List<Long> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getLong(1));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String selectString() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean selectBoolean() {
        try (PreparedStatement ps = prepare(); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBoolean(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Map 결과 -> 객체 매핑
    public <T> List<T> selectRows(Class<T> cls) {
        List<Map<String, Object>> rows = selectRows();
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(mapToClass(row, cls));
        return result;
    }

    public <T> T selectRow(Class<T> cls) {
        Map<String, Object> row = selectRow();
        return row == null ? null : mapToClass(row, cls);
    }

    /**
     * Map<String,Object> -> 객체 매핑
     * - 필드명이 컬럼명과 같아야 함
     * - primitive/Wrapper 타입 호환 처리
     */
    private <T> T mapToClass(Map<String, Object> row, Class<T> cls) {
        try {
            T obj = cls.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String col = e.getKey();
                Object val = e.getValue();
                try {
                    Field f = cls.getDeclaredField(col);
                    f.setAccessible(true);
                    if (val != null) {
                        Class<?> fieldType = f.getType();
                        if (fieldType == boolean.class && val instanceof Boolean) f.setBoolean(obj, (Boolean) val);
                        else if (fieldType == Boolean.class && val instanceof Boolean) f.set(obj, val);
                        else if (fieldType == Long.class && val instanceof Number) f.set(obj, ((Number) val).longValue());
                        else if (fieldType == long.class && val instanceof Number) f.setLong(obj, ((Number) val).longValue());
                        else f.set(obj, val);
                        continue;
                    }
                    f.set(obj, val);
                } catch (NoSuchFieldException ignored) {
                    // 엔티티에 해당 컬럼 필드가 없으면 무시
                }
            }
            return obj;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
