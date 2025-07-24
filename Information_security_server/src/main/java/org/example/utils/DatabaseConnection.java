package org.example.utils;

import org.example.models.User;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public class DatabaseConnection {
    private static String JDBC_URL;
    private static String USERNAME;
    private static String PASSWORD;
    private static boolean propertiesLoaded = false;

    public DatabaseConnection() {

    }

    private static void loadDatabaseProperties() {
        Properties properties = new Properties();
        try (InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find database.properties. Please ensure it's in src/main/resources.");
                return;
            }

            properties.load(input);
            JDBC_URL = properties.getProperty("db.url");
            USERNAME = properties.getProperty("db.username");
            PASSWORD = properties.getProperty("db.password");
            propertiesLoaded = true;

            try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
                System.out.println("Connection to MySQL database established successfully!");
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error loading database properties: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static Connection getConnection() {
        if (!propertiesLoaded) {
            loadDatabaseProperties();
        }
        if (JDBC_URL == null || USERNAME == null || PASSWORD == null) {
            System.err.println("Cannot establish connection: Database properties are not loaded.");
            return null;
        }
        try {
            Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
            System.out.println("Connection to database established successfully!");
            return connection;
        } catch (SQLException e) {
            System.err.println("Failed to establish database connection: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void closeResources(Connection connection, Statement statement, ResultSet resultSet) {
        try {
            if (resultSet != null) resultSet.close();
            if (statement != null) statement.close();
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            System.err.println("Error closing database resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createUsersTable(Connection connection) {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(255) NOT NULL," +
                "email VARCHAR(255) UNIQUE NOT NULL" +
                ");";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            System.out.println("Table 'users' created or already exists.");
        } catch (SQLException e) {
            System.err.println("Error creating table 'users': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static User getUserByEmailAndPassword(String email, String password) {
        String sql = "SELECT id, name, email FROM users WHERE email = ? AND password = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = getConnection();
            if (connection == null) return null;

            statement = connection.prepareStatement(sql);
            statement.setString(1, email);
            statement.setString(2, password);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                return new User(id, name, email, password);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, statement, resultSet);
        }
        return null;
    }

    public static User getUserByRefreshToken(String refreshToken) {
        String sql = "SELECT u.id, u.name, u.email, u.password FROM users u " +
                    "JOIN refresh_tokens rt ON u.id = rt.user_id " +
                    "WHERE rt.token = ? AND rt.expires_at > NOW()";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = getConnection();
            if (connection == null) return null;

            statement = connection.prepareStatement(sql);
            statement.setString(1, refreshToken);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                String email = resultSet.getString("email");
                String password = resultSet.getString("password");
                return new User(id, name, email, password);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, statement, resultSet);
        }
        return null;
    }

    public static void saveRefreshToken(int userId, String token) {
        // Set expiration to 7 days from now
        java.sql.Timestamp expiresAt = new java.sql.Timestamp(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
        String sql = "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE token = VALUES(token), expires_at = VALUES(expires_at)";
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = getConnection();
            if (connection == null) return;
            statement = connection.prepareStatement(sql);
            statement.setInt(1, userId);
            statement.setString(2, token);
            statement.setTimestamp(3, expiresAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, statement, null);
        }
    }

    public static boolean isRefreshTokenValid(int userId, String token) {
        String sql = "SELECT expires_at FROM refresh_tokens WHERE user_id = ? AND token = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            if (connection == null) return false;
            statement = connection.prepareStatement(sql);
            statement.setInt(1, userId);
            statement.setString(2, token);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                java.sql.Timestamp expiresAt = resultSet.getTimestamp("expires_at");
                return expiresAt != null && expiresAt.after(new java.util.Date());
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            closeResources(connection, statement, resultSet);
        }
    }

    public static void createRefreshTokensTable(Connection connection) {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS refresh_tokens (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "token VARCHAR(255) UNIQUE NOT NULL," +
                "expires_at DATETIME NOT NULL," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            System.out.println("Table 'refresh_tokens' created or already exists.");
        } catch (SQLException e) {
            System.err.println("Error creating table 'refresh_tokens': " + e.getMessage());
            e.printStackTrace();
        }
    }

}
