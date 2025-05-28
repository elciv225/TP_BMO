# Project Documentation

This document provides instructions for building the client application, configuring network settings, deploying the server, and using the meeting management features.

## Client Application

### Building the Client Executable JAR

To build the client application into an executable JAR file, you need Apache Maven installed.

1.  **Open a terminal or command prompt.**
2.  **Navigate to the project's root directory** (where the `pom.xml` file is located).
3.  **Run the Maven command:**
    ```bash
    mvn clean package
    ```
4.  This command will clean the project, compile the source code, run tests (if not skipped in `pom.xml`), and package the application into a JAR file.
5.  **Locate the generated JAR:** The executable JAR file will be created in the `target/` directory. The typical name will be `TP_BMO-1.0-SNAPSHOT.jar`.

### Network Configuration

The client application needs to know the IP address and port of the Tyrus WebSocket server to connect.

1.  **Server Address:** The server address (hostname or IP) and port are configured within the client's source code.
    *   Specifically, you can find this configuration in the `src/main/java/client/ClientWebSocket.java` file.
    *   Look for the URI string used when initializing the WebSocket connection, typically like:
        ```java
        // Example from ClientWebSocket constructor or a connect method:
        // new URI("ws://localhost:8080/websocket/" + URLEncoder.encode(ipClient, StandardCharsets.UTF_8.name()));
        ```
    *   To change the server address, modify `"localhost"` to the server's actual IP address or hostname.
2.  **Default Server Port:** The server is configured to run on port `8080` by default (as seen in `ServeurWebSocket.java` and `docker-compose.yaml`). If the server's port is changed, you must update it in the client's connection URI as well.

## Server Deployment

The server and its database can be easily deployed using Docker and Docker Compose.

### Deploying with Docker Compose

1.  **Prerequisites:**
    *   Ensure Docker is installed and running on your system.
    *   Ensure Docker Compose is installed.
2.  **Open a terminal or command prompt.**
3.  **Navigate to the project's root directory** (where the `docker-compose.yaml` file is located).
4.  **Run the Docker Compose command:**
    ```bash
    docker-compose up -d
    ```
    *   The `-d` flag runs the containers in detached mode (in the background).
5.  **Services Started:** This command will:
    *   Build the Docker image for the Tyrus WebSocket server using the `Dockerfile` in the project root (if the image doesn't already exist locally).
    *   Start a MySQL database container named `tpbmo_db`.
    *   Start the Tyrus WebSocket server container named `tpbmo_server`.
    *   Start a phpMyAdmin container for database management, accessible at `http://localhost:8081`.
6.  **Database Schema:** The MySQL database schema is automatically initialized when the `tpbmo_db` container starts for the first time. SQL files located in the `./sql` directory (relative to the project root) are executed to create tables and populate initial data if necessary.

To stop the services, navigate to the project root in your terminal and run:
```bash
docker-compose down
```

## Meeting Management Features (Client Application)

The client application provides features for managing meetings once you have logged in and are in the "Espace Utilisateur" (User Space).

### Viewing Meetings

*   Upon successful login and navigation to the user space, the list of existing meetings should be automatically fetched from the server and displayed in the "Liste des Réunions" section.
*   The list typically shows key details like Meeting ID, Titre (Name), and Start Time.

### Creating a New Meeting

1.  In the user space, click the **"Créer une réunion"** button.
2.  A dialog window titled "Créer une Réunion" will appear.
3.  Fill in the required details for the meeting:
    *   **Nom:** The name or title of the meeting.
    *   **Sujet:** The subject or main topic.
    *   **Agenda:** A brief agenda or description.
    *   **Date:** The start date of the meeting (select from the date picker). Defaults to the current date.
    *   **Heure (HH:mm):** The start time of the meeting (e.g., `14:30`). Defaults to the current time.
    *   **Durée (min):** The duration of the meeting in minutes (e.g., `60`).
    *   **Type:** Select the type of meeting from the dropdown (e.g., `STANDARD`, `WEBINAR`).
    *   **Animateur ID (Opt.):** Optionally, the ID of the user who will animate the meeting if different from the organizer. The Organizer ID is typically automatically set to your user ID.
4.  Click the **"Créer"** button in the dialog.
5.  A notification will indicate if the meeting creation was successful or if there was an error.
6.  Upon successful creation, the meeting list should refresh to include the new meeting.

### Inviting Users to a Meeting

1.  **Select a Meeting:** From the "Liste des Réunions", click on the meeting to which you want to invite users.
2.  **Enter User Identifier:** In the "Inviter un utilisateur à la réunion sélectionnée:" section, type the ID (or other identifier as configured by the system, typically the User ID) of the person you wish to invite into the text field (e.g., "ID ou email de l'utilisateur").
3.  **Click "Inviter":** Press the **"Inviter"** button.
4.  A notification will indicate if the invitation was sent successfully or if there was an error (e.g., user not found, already invited, meeting not found).

This covers the basic functionalities for managing meetings through the client application.The `DOCUMENTATION.md` file has been created successfully with the planned content.

It includes sections for:
*   **Client Application**:
    *   Building the client JAR using Maven.
    *   Configuring the client's network settings to connect to the server, including how to modify the server address and the default port.
*   **Server Deployment**:
    *   Using `docker-compose up -d` to deploy the server and database.
    *   Mention of automatic database schema loading.
*   **Meeting Management Features (Client Application)**:
    *   How to view the list of meetings.
    *   Step-by-step instructions on how to create a new meeting using the client UI.
    *   Step-by-step instructions on how to invite users to a selected meeting using the client UI.

The documentation is structured with headings and code blocks for clarity and conciseness, providing users with the necessary information to build, deploy, and use the application.
