package org.example;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SimpleSocketServer {
    public static void main(String[] args) {
        int port = 8080; // порт, который сервер будет слушать
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());

                // Отправляем простой ответ клиенту
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);
                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-Type: text/plain");
                writer.println("Content-Length: 13");
                writer.println();
                writer.println("Hello, World!");

                socket.close();
            }
        } catch (Exception e) {
            System.out.println("Error in server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
