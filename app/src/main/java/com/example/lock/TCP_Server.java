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
        int port = 30000;
        serverSocket = new ServerSocket(port);
        System.out.println("server start success");
        while (true) {
            Socket socket = serverSocket.accept();
            //Socket to printStream
            PrintStream ps = new PrintStream(socket.getOutputStream());
            ps.println("message form server");

            InputStream inputStream = socket.getInputStream();
            byte[] bytesFromClient = new byte[1024];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = inputStream.read(bytesFromClient)) != -1) {
                sb.append(new String(bytesFromClient, 0, len));
            }
            System.out.println("get message from client: " + sb);
            result_judge.post(() -> result_judge.setText("暂时通过"));

            //close
            inputStream.close();
            ps.close();
            socket.close();
        }
    }
}