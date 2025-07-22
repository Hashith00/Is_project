# Information Security Project

This project is a Java-based information security system with separate client and server modules. It demonstrates secure communication, authentication, and data management using modern Java, MySQL, and JWT technologies.

## Features

- **Client-Server Architecture:** Separate modules for client and server, communicating securely.
- **User Authentication:** JWT-based authentication for secure access.
- **MySQL Integration:** Persistent data storage using MySQL.
- **JSON Processing:** Uses org.json for data interchange.
- **Modular Design:** Easily extensible for additional security features.

## Project Structure

- `Information_secuity_project_client/` — Java client application (Maven project)
- `Information_security_server/` — Java server application (Maven project)
- `.DS_Store` — System file (can be ignored)
- `README.md` — Project documentation

## Setup Instructions

### 1. Clone the Repository

```sh
git clone https://github.com/Hashith00/Is_project.git
cd Is_project
```

### 2. Configure MySQL

- Ensure MySQL is installed and running.
- Create a database and user as required by your application.
- Update the database connection settings in both client and server modules (typically in a `config` or `application.properties` file).

### 3. Build the Projects

Build both client and server using Maven:

```sh
cd Information_secuity_project_client
mvn clean package

cd ../Information_security_server
mvn clean package
```

### 4. Run the Server

```sh
cd Information_security_server
# Example, replace with actual main class if needed
java -jar target/Information_security_server-1.0-SNAPSHOT.jar
```

### 5. Run the Client

```sh
cd Information_secuity_project_client
# Example, replace with actual main class if needed
java -jar target/Information_secuity_project_client-1.0-SNAPSHOT.jar
```

## Notes

- Ensure the server is running before starting the client.
- Both modules require Java 19 or higher.
- MySQL server must be accessible to both client and server.
- JWT and JSON dependencies are managed via Maven.

## Requirements

- Java 19+
- Maven
- MySQL



*Please update the database configuration and main class names as per your implementation. For more details, refer to the `pom.xml` files in each module.*
