package org.example;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws SQLException, InterruptedException {


        // JDBC 접속을 위한 정보
        String url = "jdbc:mysql://localhost:3306/babel?serverTimezone=UTC&characterEncoding=UTF-8";
        String user = "root";
        String password = "1234";

        SimpleConnectionPool pool = SimpleConnectionPool.getInstance(url, user, password, 5); // singleton

        // 각 스레드가 수행할 작업 정의
        Runnable task = () -> {
            try {
                Connection connection = pool.getConnection(5000); // 커넥션 요청

                // 실제적으로 sleep 부분이 DB 처리(쿼리) 부분이 된다.
                Thread.sleep(500); // 테스트를 위한 sleep -> 만약 sleep 하지 않으면 커넥션을 획득하자마자 반납하여 커넥션 풀에 커넥션이 없을 때 처리방식을 알 수 없음


                pool.releaseConnection(connection); // 커넥션 반환

            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
            }
        };

        // 스레드 20개 생성 후 작업 시작
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }

        // join() -> 현재 스레드(main)가 실행을 중지하고 join이 호출된 스레드가 작업을 완료할 때까지 기다리게한다.
        for (Thread thread : threads) {
            thread.join();
        }

        // 작업이 끝났다면 자원 반납
        try {
            pool.closePool();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }



}
