package com.back;



import java.sql.*;

/*

DiriverManager.getConnection(...)을 매번 호출하면  소켓 연결 -> 로그인 인증 -> 세션 생성 비용이 크다
커넥션 풀은 이런 문제를 해결하기 위해 여러개의 DB Connection을 만들어 두고 필요할 때 꺼내 쓰고 다 쓰면 반납하는 공용 커넥션 창고
 */


public class SimpleDb {

    private final String url;
    private final String user;
    private final String password;
    private boolean mode;

    /*
    DB와 직접 통신하려면 매번 연결을 새로 해야 한다
    그 과정은 소켓 연결 -> 로그인 인증 -> 세선 생성이므로 매번 새로 연결하려면 시간이 걸린다
    따라서 Connection을 재사용하는 것이 중요하다

    트랜잭션 내에서는 Connection을 계속 사용해야 한다
     */
    private Connection txConnection = null;


    public SimpleDb(String host, String user, String password, String dbName) {
        this.url = "jdbc:mysql://" + host + "/" + dbName + "?serverTimezone=Asia/Seoul";
        this.user = user;
        this.password = password;


    }

    public void setDevMode(boolean mode) {
        this.mode = mode;
    }


    // SQL 한번 실행
    public void run(String sql, Object ... values) {
        try (
            Connection conn = DriverManager.getConnection(url, user, password);
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        } 
        catch (SQLException e) {
            throw new RuntimeException("SQL 실행 오류: " + e.getMessage(), e);
        }

    }

    // sql 객체 반환
    public Sql genSql() {
        try {
            if(txConnection != null && !txConnection.isClosed()) {
                //트랜잭션 중이면 닫지 않도록 autoClose = false
                return new Sql(txConnection, false);
            }
            else {
                //트랜잭션 중이 아니라면 단발성으로
                return new Sql(DriverManager.getConnection(url, user, password), true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //트랜잭션 커넥션 무조건 종료
    public void close() {
        try {
            if (txConnection != null && !txConnection.isClosed()) {
                txConnection.close();
                txConnection = null;
            }
        } catch (SQLException ignore) {}
    }

    // ====트랜잭션 처리====


    public void startTransaction() {
        try {
            // txConnection이 없거나 닫혀 있으면 새로 연결 생성
            if (txConnection == null || txConnection.isClosed()) {
                txConnection = DriverManager.getConnection(url, user, password);
                txConnection.setAutoCommit(false); // 트랜잭션 모드로 설정
            }
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 시작 오류: " + e.getMessage(), e);
        }
    }



    public void rollback() {
        try {
            if(txConnection != null && !txConnection.isClosed()) { //현재 연결 중이라면 롤백 후 연결을 끊어낸다
                txConnection.rollback();
                txConnection.close();
                txConnection = null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 롤백 오류: " + e.getMessage(), e);
        }
    }

    public void commit() {
        try {
            if(txConnection != null && !txConnection.isClosed()) { //현재 연결 중이라면 롤백 후 연결을 끊어낸다
                txConnection.commit();
            }
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 커밋 오류: " + e.getMessage(), e);
        }
    }
}
