package com.back.simpleDb;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 문자열로 SQL을 조립 및 구현(append, appendIn)
 * 파라미터는 JDBC 바인딩 대상으로 축적
 * 최종적으로 SimpleDb의 커넥션/헬퍼를 이용하여 실행
 * 결과는 스칼라, Map, DTO로 반환
 * 스레드 안전성
 * Sql 인스턴스는 사용단위로 만들어지고 스레드 간 공유하지 않는것을 전제 => SimpleDb.genSql()로 새로운 객체 만듬
 **/



public class Sql {

    private final SimpleDb db; // 실행 시 사용할 SimpleDb

    private final StringBuilder sb = new StringBuilder();
    private final List<Object> params = new ArrayList<>(); // JDBC 바인딩 파라미터

    Sql(SimpleDb db) {
        this.db = db;
    }

    // sql 빌더
    public Sql append(String sqlPart, Object... bindParams) {
        if (sqlPart != null && !sqlPart.isBlank()) {
            sb.append(sqlPart.trim()).append(" ");
        }
        if (bindParams != null && bindParams.length > 0) {
            params.addAll(SimpleDb.flattenParams(bindParams));
        }
        return this;
    }

    // 가변 길이 파라미터를 placeholder 확장으로 주입
    // appendIn("WHERE id IN (?)", 1,2,3)   → "WHERE id IN (?, ?, ?)" + [1,2,3]
    // appendIn("ORDER BY FIELD(id, ?)", 2,1,3) → "ORDER BY FIELD(id, ?, ?, ?)" + [2,1,3]
    // appendIn("VALUES (NOW(), NOW(), ?)", "제목", "내용") → "VALUES (NOW(), NOW(), ?, ?)" + ["제목","내용"]
    // t012, t013, t014
    public Sql appendIn(String sqlPartWithPlaceholder, Object... values) {
        Objects.requireNonNull(sqlPartWithPlaceholder, "sqlPartWithPlaceholder");
        // 배열,컬렉션,단일값 전부 1차원 리스트로
        List<Object> flat = SimpleDb.flattenParams(values);

        String part = sqlPartWithPlaceholder;
        if (flat.isEmpty()) {
            // 값이 비었으면 "(?)" → "(NULL)", "?" → "NULL" (안전하게 처리하기 위함)
            part = part.replace("(?)", "(NULL)").replace("?", "NULL");
            sb.append(part).append(" ");
            return this;
        }

        int idx = part.indexOf("(?)"); // "(?)" 패턴을 찾아 확장하고, 없으면 첫 번째 "?" 확장

        if (idx >= 0) {
            String ph = repeatPlaceholders(flat.size());
            part = part.substring(0, idx) + "(" + ph + ")" + part.substring(idx + 3);
        } else {
            // "(?)"가 없으면 첫 번째 "?"를 "?, ?, ?" 로 확장
            int q = part.indexOf('?');
            if (q < 0) {
                throw new IllegalArgumentException("placeholder '?' 필요: " + sqlPartWithPlaceholder);
            }
            String ph = repeatPlaceholders(flat.size());
            part = part.substring(0, q) + ph + part.substring(q + 1);
        }

        sb.append(part).append(" ");
        params.addAll(flat); // 확장된 placeholder 개수만큼 값 추가
        return this;
    }

    // values 개수만큼 "?, ?, ?" 형태의 placeholder 문자열 생성
    private static String repeatPlaceholders(int n) {
        StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; i < n; i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    // 실행 관련 (INSERT 실행 후 생성된 AUTO_INCREMENT의 PK 반환)
    // t001
    public long insert() {
        db.log(this);
        try (PreparedStatement ps = db.prepare(toSql(), true)) {
            SimpleDb.bindParams(ps, params);
            int affected = ps.executeUpdate();
            if (affected == 0) { return 0L; }
            try (ResultSet rs = ps.getGeneratedKeys()) {
                // 첫 번째 컬럼이 생성 키
                if (rs.next()) { return rs.getLong(1); }
            }
            return affected; // 생성키가 없다면 영향 행수 반환
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // t002
    public int update() {
        return dml();
    }

    // t003
    public int delete() {
        return dml();
    }

    // 공통 DML 처리
    // t002, t003
    private int dml() {
        db.log(this);
        try (PreparedStatement ps = db.prepare(toSql(), false)) {
            SimpleDb.bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 첫 칼럼 long 반환 , 다양한 타입을 long으로 정규화
    // t007, t012, t013, t014
    public Long selectLong() {
        Object v = selectScalar();
        if (v == null) { return null; }
        if (v instanceof Number n) { return n.longValue(); }
        if (v instanceof String s && !s.isBlank()) { return Long.parseLong(s); }
        if (v instanceof Boolean b) { return b ? 1L : 0L; }
        if (v instanceof byte[] bytes && bytes.length > 0) { return bytes[0] != 0 ? 1L : 0L; }
        return Long.parseLong(String.valueOf(v));
    }

    // 여러행일때 => 첫 컬럼을 Long 리스트로 반환, order by 검증
    // SELECT 결과의 첫 번째 컬럼만 사용
    // t014
    public List<Long> selectLongs() {
        db.log(this);
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = db.prepare(toSql(), false)) {
            SimpleDb.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object v = rs.getObject(1);
                    if (v instanceof Number n)  { out.add(n.longValue()); }
                    else if (v instanceof String s && !s.isBlank()) { out.add(Long.parseLong(s)); }
                    else if (v instanceof Boolean b) { out.add(b ? 1L : 0L); }
                    else if (v instanceof byte[] bytes && bytes.length > 0) { out.add(bytes[0] != 0 ? 1L : 0L); }
                    else { out.add(Long.parseLong(String.valueOf(v))); }
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 첫 칼럼 string 반환
    // t008
    public String selectString() {
        Object v = selectScalar();
        return v == null ? null : String.valueOf(v);
    }

    // 첫 칼럼 boolean 반환
    // t009, t010, t011
    public Boolean selectBoolean() {
        Object v = selectScalar();
        if (v == null) { return null; }
        if (v instanceof Boolean b) { return b; }
        if (v instanceof Number n) { return n.intValue() != 0; }
        if (v instanceof String s) {
            return switch (s.trim().toLowerCase()) {
                case "1", "y", "yes", "true", "t" -> true;
                default -> false;
            };
        }
        if (v instanceof byte[] bytes && bytes.length > 0) { return bytes[0] != 0; }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    // 첫 컬럼 LocalDateTime 반환
    // t006
    public LocalDateTime selectDatetime() {
        Object v = selectScalar();
        if (v == null) { return null; }
        if (v instanceof LocalDateTime ldt) { return ldt; }
        if (v instanceof Timestamp ts) { return ts.toLocalDateTime(); }
        if (v instanceof String s) {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        }
        throw new IllegalStateException("지원하지 않는 DATETIME 타입입니다: " + v.getClass());
    }

    // 1행 1컬럼만 읽어서 반환 (공통)
    private Object selectScalar() {
        db.log(this);
        try (PreparedStatement ps = db.prepare(toSql(), false)) {
            SimpleDb.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { return null; }
                Object v = rs.getObject(1);
                if (v instanceof Timestamp ts) { return ts.toLocalDateTime(); }
                if (v instanceof byte[] bytes && bytes.length > 0) { return bytes[0] != 0; }
                return v;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 단건 Map 조회 (없으면 null)
    // t005
    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // 다건 Map 조회 (동적 매핑)
    // t004
    public List<Map<String, Object>> selectRows() {
        db.log(this);
        try (PreparedStatement ps = db.prepare(toSql(), false)) {
            SimpleDb.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return SimpleDb.toListOfMaps(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 단건 DTO 조회 (없으면 null)
    // t016
    public <T> T selectRow(Class<T> type) {
        List<T> rows = selectRows(type);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // 다건 DTO 조회 (Map 정규화)
    // t015
    public <T> List<T> selectRows(Class<T> type) {
        db.log(this);
        try (PreparedStatement ps = db.prepare(toSql(), false)) {
            SimpleDb.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return db.toList(rs, type);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // SQL 문자열 반환 (공백 제거), 로깅 시 전체 테스트케이스에 사용
    public String toSql() {
        return sb.toString().trim();
    }

    // 외부에서 파라미터 목록을 읽기 전용으로 확인
    public List<Object> getParams() {
        return Collections.unmodifiableList(params);
    }
}
