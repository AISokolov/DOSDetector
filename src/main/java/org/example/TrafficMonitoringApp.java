package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;

public class TrafficMonitoringApp {

    private static final String DB_URL = "jdbc:mariadb://localhost:3306/traffic_monitoring";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "ImM3dv3d";

    private static final Map<String, List<Long>> trafficData = new HashMap<>(); // IP -> List of timestamps
    private static final int WINDOW_SIZE = 10; // Количество последних запросов для расчёта среднего
    private static final double BOLLINGER_FACTOR = 2.0; // Коэффициент для определения волатильности

    public static void main(String[] args) {
        createDatabaseIfNotExists();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nTraffic Monitoring App");
            System.out.println("1. Start Monitoring");
            System.out.println("2. Show Suspicious IPs");
            System.out.println("3. Exit");
            System.out.print("Select an option: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case 1:
                    startMonitoring();
                    break;
                case 2:
                    showSuspiciousIPs();
                    break;
                case 3:
                    System.out.println("Exiting...");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void startMonitoring() {
        System.out.println("Starting traffic monitoring...");
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "C:\\Users\\Alexander\\Downloads\\WinDump.exe", "-i", "1", "port", "8080"
            );

            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null) {

                System.out.println("Captured packet: " + line);
                String ip = extractIP(line);
                if (ip != null) {
                    analyzeTraffic(ip);
                }
            }
        } catch (Exception e) {
            System.out.println("Error during monitoring: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void analyzeTraffic(String ip) {
        long currentTime = System.currentTimeMillis();

        // Добавляем текущую метку времени для IP
        trafficData.putIfAbsent(ip, new ArrayList<>());
        List<Long> timestamps = trafficData.get(ip);
        timestamps.add(currentTime);

        // Удаляем старые метки времени
        timestamps.removeIf(timestamp -> currentTime - timestamp > 60000); // Последняя минута

        if (timestamps.size() > WINDOW_SIZE) {
            double average = calculateAverage(timestamps);
            double stdDev = calculateStandardDeviation(timestamps, average);

            double upperBand = average + BOLLINGER_FACTOR * stdDev;
            double lowerBand = average - BOLLINGER_FACTOR * stdDev;

            System.out.printf("IP: %s, Average: %.2f, StdDev: %.2f, Upper: %.2f, Lower: %.2f\n",
                    ip, average, stdDev, upperBand, lowerBand);

            if (timestamps.size() > upperBand) {
                System.out.println("Suspicious traffic detected for IP: " + ip);
                saveSuspiciousIPToDatabase(ip);
            }
        }
    }

    private static double calculateAverage(List<Long> timestamps) {
        return timestamps.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private static double calculateStandardDeviation(List<Long> timestamps, double average) {
        double variance = timestamps.stream()
                .mapToDouble(timestamp -> Math.pow(timestamp - average, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static String extractIP(String packet) {
        String[] parts = packet.split(" ");
        for (String part : parts) {
            if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                return part;
            }
        }
        return null;
    }

    private static void saveSuspiciousIPToDatabase(String ip) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("INSERT IGNORE INTO suspicious_ips (ip) VALUES (?)")) {
            stmt.setString(1, ip);
            stmt.executeUpdate();
            System.out.println("Suspicious IP saved: " + ip);
        } catch (SQLException e) {
            System.out.println("Error saving IP to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void showSuspiciousIPs() {
        System.out.println("Suspicious IPs:");
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT ip FROM suspicious_ips");
            while (rs.next()) {
                System.out.println(rs.getString("ip"));
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving IPs from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDatabaseIfNotExists() {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/", DB_USER, DB_PASSWORD);
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS traffic_monitoring");
            stmt.executeUpdate("USE traffic_monitoring");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS suspicious_ips (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "ip VARCHAR(255) UNIQUE NOT NULL)");
        } catch (SQLException e) {
            System.out.println("Error creating database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
