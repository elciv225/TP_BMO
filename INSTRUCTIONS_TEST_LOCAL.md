# Instructions for Local Testing

This document provides step-by-step instructions to set up, run, and test the application on your local machine.

## 1. Prerequisites

Before you begin, ensure you have the following software installed and configured on your system:

*   **Java Development Kit (JDK):** Version 21. You can download it from [OpenJDK](https://jdk.java.net/21/) or other providers like Adoptium.
*   **Apache Maven:** A build automation tool. You can download it from the [Maven website](https://maven.apache.org/download.cgi) and follow their installation instructions.
*   **Docker:** A platform for developing, shipping, and running applications in containers. Get it from the [Docker website](https://www.docker.com/products/docker-desktop/).
*   **Docker Compose (v2):** A tool for defining and running multi-container Docker applications. It's typically included with Docker Desktop. Ensure you can use the `docker compose` command (with a space).
*   **Git Client:** For cloning the repository. Download from the [Git website](https://git-scm.com/downloads).

Verify installations by running commands like `java -version`, `mvn -version`, `docker --version`, `docker compose version`, and `git --version` in your terminal.

## 2. Setup

1.  **Clone the Repository:**
    If you haven't already, clone the project repository to your local machine:
    ```bash
    git clone <repository_url>
    ```
    (Replace `<repository_url>` with the actual URL of the Git repository.)

2.  **Navigate to Project Directory:**
    Open a terminal or command prompt and change to the root directory of the cloned project:
    ```bash
    cd <project_directory_name>
    ```
    (Replace `<project_directory_name>` with the name of the project folder, e.g., `TP_BMO`).

## 3. Running the Database (MySQL)

The application uses a MySQL database, which can be run as a Docker container using Docker Compose.

1.  **Start the Database Service:**
    In your terminal, from the project root directory, run:
    ```bash
    docker compose up -d tpbmo_db
    ```
    *   This command starts the `tpbmo_db` service in detached mode (`-d`).
    *   The `docker-compose.yaml` file in the project root configures this service.
    *   **Port Configuration:** The database will be accessible on port `3307` on your host machine, mapped to port `3306` inside the container.
    *   **Database Initialization:** The database schema and initial data will be loaded from SQL files located in the `./sql` directory (e.g., `tpbmo.sql`) when the container starts for the first time.

2.  **Check if the Database is Running:**
    You can check the status of the Docker containers:
    ```bash
    docker ps
    ```
    You should see a container for the `tpbmo_db` service running. To view its logs:
    ```bash
    docker compose logs tpbmo_db
    ```

## 4. Building the Application

Compile the Java source code and package the application using Maven.

1.  **Build with Maven:**
    In your terminal, from the project root directory, run:
    ```bash
    mvn clean package
    ```
    *   This command cleans the project, compiles all Java code, runs any tests, and packages the application.
    *   It creates the client's executable JAR file (e.g., `TP_BMO-1.0-SNAPSHOT.jar`) in the `target/` directory.
    *   It also copies runtime dependencies for the server (like HikariCP, MySQL Connector) into the `target/server-libs/` directory, as configured by the `maven-dependency-plugin`.

## 5. Running the Java Server

The server is a Java application that handles WebSocket connections and business logic.

1.  **Classpath Requirements:**
    The server needs several groups of JARs in its classpath:
    *   The compiled server classes (from `target/classes/`).
    *   Dependencies managed by Maven and copied to `target/server-libs/` (e.g., HikariCP, MySQL Connector, SLF4J).
    *   System-scoped dependencies manually placed in the `lib/` folder (e.g., Tyrus, Grizzly, JavaFX for server-side models if shared).

2.  **Run the Server:**
    Open a new terminal or command prompt in the project root directory.

    *   **On Linux/macOS:**
        ```bash
        java -cp "target/classes:target/server-libs/*:lib/*" serveur.ServeurWebSocket
        ```
    *   **On Windows:**
        ```batch
        java -cp "target/classes;target/server-libs/*;lib/*" serveur.ServeurWebSocket
        ```
    *   Note: If you encounter "No main manifest attribute" errors with the above, ensure `target/classes` contains all compiled server code and `serveur.ServeurWebSocket` is the correct main class. The classpath separator is `:` for Linux/macOS and `;` for Windows.

3.  **Server Status:**
    If successful, you should see a message like "Serveur WebSocket démarré sur ws://localhost:8080/websocket". The server is now listening for WebSocket connections on port `8080`.

## 6. Running the JavaFX Client

The client is a JavaFX application.

1.  **Run the Client JAR:**
    Open another new terminal or command prompt. Navigate to the project root directory.
    Run the following command (replace `TP_BMO-1.0-SNAPSHOT.jar` if your JAR name is different):
    ```bash
    java -jar target/TP_BMO-1.0-SNAPSHOT.jar
    ```
    *   The `maven-shade-plugin` is configured to create an executable "uber-JAR" that should include all necessary dependencies, including JavaFX modules from the `lib/` directory.

2.  **Connect to Server:**
    *   The client application window should open. The first screen (`connexionServeur.fxml`) will prompt you to enter the server IP address.
    *   If running the server on the same machine, you can typically use `localhost` or `127.0.0.1`.
    *   Click the connect button.

## 7. Testing Functionalities (Brief Guide)

1.  **Connection & Login:**
    *   After connecting to the server IP, the login screen (`authentification.fxml`) should appear.
    *   Log in using existing credentials. If you don't have any, you might need to:
        *   Add users directly to the database using a tool like PhpMyAdmin (accessible at `http://localhost:8081` if the `phpmyadmin` service is up via `docker compose up -d phpmyadmin`).
        *   Use credentials from the `./sql/tpbmo.sql` initialization script if any are defined there.
        *   (Note: User registration functionality within the client application itself would be a separate feature.)

2.  **Meeting Management (in Espace Utilisateur):**
    *   **View Meetings:** Upon successful login, the list of meetings should appear.
    *   **Create Meeting:**
        *   Click "Créer une réunion".
        *   Fill in the dialog (Nom/Title, Sujet, Agenda, Date, Heure, Durée, Type, Animateur ID (optional)).
        *   Click "Créer". The meeting list should update.
    *   **Enter Meeting:**
        *   Select a meeting from the list.
        *   The "Entrer Réunion Sélectionnée" button (if added and enabled) should become active. Click it.
        *   This should navigate you to the `reunion.fxml` view for that specific meeting.

3.  **In-Meeting Chat (in Réunion View):**
    *   **View Details:** The meeting title and agenda should be displayed.
    *   **Message History:** Past messages for the meeting should load and display in the chat area.
    *   **Send Messages:** Type a message in the input field and click "Envoyer".
    *   **Verify Broadcast:** If you have multiple clients connected to the same meeting, messages sent by one should appear for others.

4.  **Exiting Meeting:**
    *   Click "Quitter la Réunion". This should take you back to the "Espace Utilisateur" view with the meeting list.

## 8. Stopping the Application

1.  **Client:** Close the JavaFX client window.
2.  **Server:** Go to the terminal where the Java server (`serveur.ServeurWebSocket`) is running and press `Ctrl+C` to stop it.
3.  **Database:**
    *   To stop only the database container:
        ```bash
        docker compose stop tpbmo_db
        ```
    *   To stop all services defined in `docker-compose.yaml` (including the database and potentially phpMyAdmin if you started it):
        ```bash
        docker compose down
        ```
    *   If you also want to remove the database volume (all data will be lost):
        ```bash
        docker compose down -v
        ```

This concludes the local testing instructions.
