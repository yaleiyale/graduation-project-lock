package com.example.lock;

import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TCP_Server {
    private ServerSocket serverSocket;

    public void openDoor(TextView result_judge) throws IOException {
        //监听端口
        int port = 30000;
        //创建一个ServerSocket, 用于监听客户端Socket的连接请求

        serverSocket = new ServerSocket(port);
        System.out.println("server启动成功，等待客户端连接...");
        while (true) {
            Socket socket = serverSocket.accept();
            //将Socket对应的输出流包装成printStream
            PrintStream ps = new PrintStream(socket.getOutputStream());
            ps.println("You have received message form server!");

            InputStream inputStream = socket.getInputStream();
            byte[] bytesFromClient = new byte[1024];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = inputStream.read(bytesFromClient)) != -1) {
                sb.append(new String(bytesFromClient, 0, len));
            }
            System.out.println("get message from client: " + sb);
            result_judge.post(() -> result_judge.setText("暂时通过"));
            //关闭输出流，关闭socket

            inputStream.close();
            ps.close();
            socket.close();
        }
    }
}