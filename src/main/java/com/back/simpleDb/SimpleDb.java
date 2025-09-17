package com.back.simpleDb;

import java.sql.*;

/**
 * SimpleDb 클래스
 * - MySQL JDBC 연결 관리
 * - Thread-local Connection 지원
 * - 트랜잭션 관리 (startTransaction, commit, rollback)
 */
public class SimpleDb {
    private final String url;
    private final String user;
    private final String password;
    private volatile boolean devMode = false; // true이면 SQL 로그 출력
    private final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();

    // 생성자: MySQL URL 구성
    public SimpleDb(String host, String user, String password, String dbName) {
        this.url = "jdbc:mysql://" + host + ":3306/" + dbName
                + "?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
        this.user = user;
        this.password = password;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    /**
     * Thread-local Connection 반환
     * - 스레드마다 독립적인 Connection 제공
     */
    Connection getConnection() throws SQLException {
        Connection conn = threadLocalConnection.get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(url, user, password);
            threadLocalConnection.set(conn);
        }
        return conn;
    }

    // Sql 객체 생성
    public Sql genSql() {
        return new Sql(this);
    }

    /**
     * 단순 SQL 실행 (INSERT, UPDATE, DELETE 등)
     */
    public void run(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * PreparedStatement 생성 및 파라미터 바인딩
     */
    PreparedStatement prepare(String sql, Object... params) throws SQLException {
        if (devMode) {
            System.out.println("== rawSql ==\n" + sql); // 개발용 SQL 로그
        }
        Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
        return ps;
    }

    /**
     * 현재 스레드 Connection 닫기
     */
    public void close() {
        Connection conn = threadLocalConnection.get();
        if (conn == null) return;
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            threadLocalConnection.remove();
        }
    }

    /**
     * 트랜잭션 시작
     */
    public void startTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 트랜잭션 커밋
     */
    public void commit() {
        try {
            Connection conn = threadLocalConnection.get();
            if (conn == null) return;
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 트랜잭션 롤백
     */
    public void rollback() {
        try {
            Connection conn = threadLocalConnection.get();
            if (conn == null) return;
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
