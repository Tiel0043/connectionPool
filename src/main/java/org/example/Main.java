package org.example;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws SQLException, InterruptedException {


        // JDBC 접속을 위한 정보
        String url = "jdbc:mysql://localhost:3306/babel?serverTimezone=UTC&characterEncoding=UTF-8";
        String user = "root";
        String password = "1234";


        SimpleConnectionPool pool = new SimpleConnectionPool(url, user, password, 5); // URL, user, password = DB 계정 정보

        // 각 스레드가 수행할 작업 정의
        Runnable task = () -> {
            try {
                Connection connection = pool.getConnection(1000); // 커넥션 요청

                Thread.sleep(500); // 테스트를 위한 sleep -> 만약 sleep 하지 않으면 커넥션을 획득하자마자 반납하여 커넥션 풀에 커넥션이 없을 때 처리방식을 알 수 없음

                pool.releaseConnection(connection); // 커넥션 반환

            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
            }
        };

        // 1. 10개의 스레드를 생성, 각 스레드마다 커넥션 요청
        for (int i = 0; i < 20; i++) {
            new Thread(task).start();
        }


        // 모든 스레드가 종료될 때까지 5초간 대기 (반납 목적)
        Thread.sleep(5000);



        // 자원 반납
        try {
            pool.closePool();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }



}
