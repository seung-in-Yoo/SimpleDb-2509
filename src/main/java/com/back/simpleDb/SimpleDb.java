package com.back.simpleDb;

import java.sql.*;

public class SimpleDb {

    private final String host;
    private final String username;
    private final String password;
    private final String dbName;

    private boolean devMode = false;

    private final ThreadLocal<Connection> conn = new ThreadLocal<>();

    public SimpleDb(String host, String username, String password, String dbName) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.dbName = dbName;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }


    public Sql genSql() {
        return new Sql(this);
    }

    public void log(String sql, Object... params) {
        if (devMode) {
            System.out.println("[SQL] " + sql);
            System.out.println("[PARAMS] " + java.util.Arrays.toString(params));
        }
    }

    public Connection getConnection() {
        try {
            Connection conn = this.conn.get();
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":3306/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true",
                        username,
                        password
                );
                this.conn.set(conn);
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            Connection conn = this.conn.get();
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.remove();
        }
    }

    public void startTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void commit() {
        try {
            Connection conn = getConnection();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rollback() {
        try {
            Connection conn = getConnection();
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        if (devMode) log(sql, params);
        return ps;
    }
}