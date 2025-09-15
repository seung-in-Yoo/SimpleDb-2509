package com.back.simpleDb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class SimpleDb {

    private final String host;
    private final String user;
    private final String password;
    private final String database;

    private final AtomicBoolean devMode = new AtomicBoolean(false);

    // 스레드별 저장소
    private final ThreadLocal<Connection> tlConn = new ThreadLocal<>();

    // DTO 매핑용 ObjectMapper
    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SimpleDb(String host, String user, String password, String database) {
        this.host = Objects.requireNonNull(host);
        this.user = user;
        this.password = password;
        this.database = Objects.requireNonNull(database);
    }

    public void setDevMode(boolean enable) {
        devMode.set(enable);
    }

    public Sql genSql() {
        return new Sql(this);
    }


    // DDL,DML 단순 실행, 테이블 생성, truncate, 데이터 삽입 등
    public int run(String sql, Object... params) {
        try {
            PreparedStatement ps = prepare(sql, false);
            bindParams(ps, flattenParams(params));
            int affected = ps.executeUpdate();
            closeQuiet(ps);
            return affected;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 트랜잭션 API
    // t018, t019
    public void startTransaction() {
        try {
            Connection c = ensureConnection();
            if (c.getAutoCommit()) {
                c.setAutoCommit(false); // 커넥션을 autocommit = false로 바꿔 여러 SQL 묶음
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 트랜잭션 commit
    // t019
    public void commit() {
        try {
            Connection c = ensureConnection();
            if (!c.getAutoCommit()) {
                c.commit();
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 트랜잭션 rollback
    // t018
    public void rollback() {
        try {
            Connection c = ensureConnection();
            if (!c.getAutoCommit()) {
                c.rollback();
                c.setAutoCommit(true); // 다음 쿼리에 대해서 다시 커밋 모드로
            }
        } catch (SQLException ignore) {
        }
    }

    // 스레드 단위로 커넥션 주기 관리 (현재 스레드 DB 커넥션 종료)
    // t017
    public void close() {
        Connection c = tlConn.get();
        if (c != null) {
            closeQuiet(c);
            tlConn.remove(); // 스레드가 쓰던 DB 연결 닫고 threadlocal에서 제거
        }
    }


    // sql에서 호출하는 내부 헬퍼 로직, 모든 테스트 케이스에서 사용
    Connection ensureConnection() throws SQLException {
        Connection c = tlConn.get();
        if (c == null || c.isClosed()) {
            String url = "jdbc:mysql://" + host + ":3306/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Seoul";
            c = (user == null)
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, user, password);
            c.setAutoCommit(true);
            tlConn.set(c);
        }
        return c;
    }

    // 모든 테스트케이스에서 공통 사용
    PreparedStatement prepare(String sql, boolean returnKeys) throws SQLException {
        Connection c = ensureConnection();
        int flags = returnKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
        return c.prepareStatement(sql, flags);
    }

    // 로깅 검증용
    void log(Sql sql) {
        if (!devMode.get()) return;
        System.out.println("== rawSql ==");
        System.out.println(sql.toSql().trim());
        if (!sql.getParams().isEmpty()) {
            System.out.println("== params ==");
            System.out.println(sql.getParams());
        }
    }

    static void closeQuiet(AutoCloseable ac) {
        if (ac == null) return;
        try {
            ac.close();
        } catch (Exception ignored) {
        }
    }

    // 파라미터 바인딩 관련 , 모든 쿼리에서 사용
    static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            // LocalDateTime => Timestamp 변환
            if (v instanceof LocalDateTime ldt) {
                ps.setTimestamp(i + 1, Timestamp.valueOf(ldt));
            } else if (v instanceof java.util.Date d) {
                // Date => Timestamp 로 변환
                ps.setTimestamp(i + 1, new Timestamp(d.getTime()));
            } else if (v instanceof Boolean b) {
                // Boolean => BIT,TINYINT 호환
                ps.setBoolean(i + 1, b);
            } else {
                // 그 외(Integer, String 등등,,) → setObject 처리
                ps.setObject(i + 1, v);
            }
        }
    }

    // SQL에서 IN, ORDER BY FIELD 등에 배열,컬렉션 파라미터를 확장하는 로직
    // t012, t013, t014 => appendIn
    static List<Object> flattenParams(Object... values) {
        if (values == null || values.length == 0) return List.of();
        List<Object> out = new ArrayList<>();
        for (Object v : values) {
            if (v == null) {
                // null => 그대로 바인딩
                out.add(null);
                continue;
            }
            if (v instanceof Collection<?> col) {
                // Collection => 내부 요소를 전부 꺼내서 추가
                out.addAll(col);
            } else if (v.getClass().isArray()) {
                // Array => 길이만큼 반복해서 추가
                int len = java.lang.reflect.Array.getLength(v);
                for (int i = 0; i < len; i++) {
                    out.add(java.lang.reflect.Array.get(v, i));
                }
            } else {
                // 일반 객체는 그대로 추가
                out.add(v);
            }
        }
        return out;
    }

    // Map, DTO 변환
    // t004, t005
    static List<Map<String, Object>> toListOfMaps(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>(); // 행을 Map 구조로 변경
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                String key = md.getColumnLabel(i);
                Object val = rs.getObject(i);

                if (val instanceof Timestamp ts) {
                    val = ts.toLocalDateTime(); // DB의 DATETIME → LocalDateTime
                }
                if (val instanceof byte[] bytes && bytes.length > 0) {
                    val = bytes[0] != 0; // DB의 BIT → Boolean
                }
                // 숫자 => Long으로
                if (val instanceof Byte b) { val = b.longValue(); }
                else if (val instanceof Short s) { val = s.longValue(); }
                else if (val instanceof Integer n) { val = n.longValue(); }
                else if (val instanceof BigInteger bi) { val = bi.longValue(); }
                else if (val instanceof BigDecimal bd) {
                    try {
                        val = bd.longValueExact();
                    } catch (ArithmeticException ex) {
                        // 소수점 있는 값은 변환 X
                    }
                }

                row.put(key, val);
            }
            list.add(row);
        }
        return list;
    }

    // t015, t016, t017
    <T> List<T> toList(ResultSet rs, Class<T> type) throws Exception {
        List<Map<String, Object>> maps = toListOfMaps(rs);
        List<T> out = new ArrayList<>(maps.size());
        for (Map<String, Object> m : maps) {
            out.add(om.convertValue(m, type));
        }
        return out;
    }
}
