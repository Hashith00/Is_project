package org.example.utils;

import org.example.model.User;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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
            System.out.println("Database properties loaded.");

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

    public static User createUser(User user) {
        String insertSQL = "INSERT INTO users (name, email) VALUES (?, ?)";
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet generatedKeys = null;
        try {
            connection = getConnection();
            if (connection == null) return null;

            preparedStatement = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, user.getName());
            preparedStatement.setString(2, user.getEmail());

            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected > 0) {
                generatedKeys = preparedStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getInt(1)); // Set the auto-generated ID
                    System.out.println("User created: " + user);
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(connection, preparedStatement, generatedKeys);
        }
        return null;
    }

    public static User getUserById(int id) {
        String selectSQL = "SELECT id, name, email FROM users WHERE id = ?";
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            if (connection == null) return null;

            preparedStatement = connection.prepareStatement(selectSQL);
            preparedStatement.setInt(1, id);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String name = resultSet.getString("name");
                String email = resultSet.getString("email");
                User user = new User(id, name, email);
                System.out.println("User retrieved: " + user);
                return user;
            } else {
                System.out.println("User with ID " + id + " not found.");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user by ID " + id + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(connection, preparedStatement, resultSet);
        }
        return null;
    }

    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String selectAllSQL = "SELECT id, name, email FROM users";
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            if (connection == null) return users;

            statement = connection.createStatement();
            resultSet = statement.executeQuery(selectAllSQL);

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                String email = resultSet.getString("email");
                users.add(new User(id, name, email));
            }
            System.out.println("Retrieved " + users.size() + " users.");
        } catch (SQLException e) {
            System.err.println("Error retrieving all users: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(connection, statement, resultSet);
        }
        return users;
    }

    public static boolean updateUser(User user) {
        String updateSQL = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            if (connection == null) return false;

            preparedStatement = connection.prepareStatement(updateSQL);
            preparedStatement.setString(1, user.getName());
            preparedStatement.setString(2, user.getEmail());
            preparedStatement.setInt(3, user.getId());

            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User updated: " + user);
                return true;
            } else {
                System.out.println("User with ID " + user.getId() + " not found for update.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error updating user " + user.getId() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeResources(connection, preparedStatement, null);
        }
    }

    public static boolean deleteUser(int id) {
        String deleteSQL = "DELETE FROM users WHERE id = ?";
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            if (connection == null) return false;

            preparedStatement = connection.prepareStatement(deleteSQL);
            preparedStatement.setInt(1, id);

            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User with ID " + id + " deleted successfully.");
                return true;
            } else {
                System.out.println("User with ID " + id + " not found for deletion.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error deleting user with ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeResources(connection, preparedStatement, null);
        }
    }

    public static boolean isValidUser(Connection connection, String username, String Stringpassword) {
        String selectSQL = "SELECT COUNT(*) FROM auth_users WHERE username = ? AND password = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, Stringpassword);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating user '" + username + "': " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public static void createTokensTable(Connection connection) {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS tokens (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "access_token TEXT NOT NULL," +
                "refresh_token TEXT NOT NULL" +
                ");";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            System.out.println("Table 'tokens' created or already exists.");
        } catch (SQLException e) {
            System.err.println("Error creating table 'tokens': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean saveAuthToken(org.example.model.AuthToken token) {
        String insertSQL = "INSERT INTO tokens (access_token, refresh_token) VALUES (?, ?)";
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            if (connection == null) return false;
            createTokensTable(connection);
            preparedStatement = connection.prepareStatement(insertSQL);
            preparedStatement.setString(1, token.getAccessToken());
            preparedStatement.setString(2, token.getRefreshToken());
            int rowsAffected = preparedStatement.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("AuthToken saved to database.");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error saving AuthToken: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(connection, preparedStatement, null);
        }
        return false;
    }
}
