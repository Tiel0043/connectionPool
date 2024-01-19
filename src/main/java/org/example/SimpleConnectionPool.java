package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

public class SimpleConnectionPool {

    private final String url;
    private final String user;
    private final String password;
    private final int maxSize;
    private final Queue<Connection> connectionPool;


    public SimpleConnectionPool(String url, String user, String password, int maxSize) { // maxSize := MaximumPoolSize
        this.url = url;
        this.user = user;
        this.password = password;
        this.maxSize = maxSize;
        this.connectionPool = new LinkedList<>();
        initializeConnectionPool();

    }

    private void initializeConnectionPool() {

        try {
            for (int i = 0; i < maxSize; i++) {
                connectionPool.add(createConnection());
            }
        } catch (SQLException e) {
            throw new RuntimeException("커넥션풀 초기화중 오류 발생", e);
        }

    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    public synchronized Connection getConnection(long timeout) throws SQLException, InterruptedException {
        long startTime = System.currentTimeMillis(); // 메소드 실행 시간을 기록

        while (connectionPool.isEmpty()) { // 커넥션 풀이 빈 경우
            System.out.println(Thread.currentThread().getName() + "커넥션 풀이 반납되길 기다리고 있습니다.");
            long elapsedTime = System.currentTimeMillis() - startTime; // 메소드의 경과 시간 계산
            long waitTime = timeout - elapsedTime; // 제한시간 - 경과 시간을 기준으로 timeout 설정
            System.out.println(Thread.currentThread().getName() + "메소드경과 시간 : " + elapsedTime);
            if (waitTime <= 0) {
                System.out.println(Thread.currentThread().getName() + " 타임아웃");
                throw new SQLException("Timeout waiting for connection");
            }
            // 연결이 반환될 때까지 기다린다.
            // wait() 메서드는 다른 스레드가 notify() 또는 notifyAll()을 호출하거나 waitTime 시간동안 대기
            wait(waitTime); // 만약 wait()로만 수행한다면 커넥션이 반납되지 않으면 무한대기상태가 될 수 있다(notify가 오지 않기에)
        }
        Connection connection = connectionPool.poll();
        System.out.println(Thread.currentThread().getName() + " 커넥션 풀 획득");

        int remainConnection = getAvailableConnectionsCount();
        System.out.println("스레드 대여 현재 개수 : " + remainConnection);

        return connection;
    }


    // 커넥션 풀 반환 알림
    public synchronized void releaseConnection(Connection connection) {
        connectionPool.offer(connection);
        int remainConnection = getAvailableConnectionsCount();
        System.out.println(Thread.currentThread().getName() + " 커넥션 풀 반납");
        System.out.println("스레드 반납 현재 개수 : " + remainConnection);
        notify(); // 다른 스레드에게 연결이 반환됨을 알려줌
    }


    public synchronized void closePool() throws SQLException{
        for (Connection connection : connectionPool) {
            connection.close();
        }
        System.out.println("DBCP Close");
        connectionPool.clear();
    }

    public synchronized int getAvailableConnectionsCount() {
        return connectionPool.size();
    }
}
