package serveur;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReunionServiceTest {

    @Mock
    private Database mockDatabase;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private ResultSet mockResultSet;
    @Mock
    private Session mockSession;
    @Mock
    private RemoteEndpoint.Basic mockBasicRemote;

    @InjectMocks
    private ReunionService reunionService; // The class we are testing

    private Map<String, Object> userProperties;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        // Mock Database.getInstance().getConnection() chain
        // Need to handle the static method getInstance()
        // For this, we use try-with-resources for MockedStatic
        // This setup will be common for many tests, so it's in @BeforeEach

        lenient().when(mockDatabase.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        lenient().when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        lenient().when(mockPreparedStatement.executeUpdate()).thenReturn(1); // Simulate 1 row affected

        // Mock Session methods
        userProperties = new HashMap<>();
        lenient().when(mockSession.getUserProperties()).thenReturn(userProperties);
        lenient().when(mockSession.getBasicRemote()).thenReturn(mockBasicRemote);
        lenient().when(mockSession.isOpen()).thenReturn(true);
    }

    // Test "envoyerMessage" action
    @Test
    void testEnvoyerMessage_Success() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class);
             MockedStatic<ServeurWebSocket> wsStaticMock = Mockito.mockStatic(ServeurWebSocket.class)) {
            
            dbStaticMock.when(Database::getInstance).thenReturn(mockDatabase);
            wsStaticMock.when(ServeurWebSocket::getSessions).thenReturn(Collections.singleton(mockSession));

            // Setup session properties for the sending user
            userProperties.put("userId", "1"); // User sending the message
            userProperties.put("reunionId", "101"); // This user is in reunion 101

            // Prepare JSON input for envoyerMessage
            JSONObject messageJson = new JSONObject();
            messageJson.put("action", "envoyerMessage");
            messageJson.put("reunionId", "101");
            messageJson.put("userId", "1"); // Personne ID from client payload
            messageJson.put("contenu", "Hello World");

            // Mock database interactions for saving message
            when(mockConnection.prepareStatement(startsWith("INSERT INTO message"))).thenReturn(mockPreparedStatement);
            
            // Mock database interactions for fetching sender's name
            when(mockConnection.prepareStatement(startsWith("SELECT nom FROM personne"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true); // User found
            when(mockResultSet.getString("nom")).thenReturn("TestUser");

            // Execute the service method
            reunionService.execute(messageJson, mockSession);

            // Verify database insert
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockConnection, times(2)).prepareStatement(sqlCaptor.capture()); // 1 for insert, 1 for select nom
            verify(mockPreparedStatement, times(1)).executeUpdate(); // For the insert
            
            // Verify that the message was "broadcast" (sent to our mocked session)
            ArgumentCaptor<String> broadcastMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockBasicRemote).sendText(broadcastMessageCaptor.capture());
            
            JSONObject sentMessage = new JSONObject(broadcastMessageCaptor.getValue());
            assertEquals("newMessage", sentMessage.getString("type"));
            assertEquals("Hello World", sentMessage.getString("content"));
            assertEquals("TestUser", sentMessage.getString("sender"));
        }
    }

    // Test "inviterMembre" action - Successful invitation by organizer
    @Test
    void testInviterMembre_SuccessByOrganizer() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getInstance).thenReturn(mockDatabase);

            // Inviter is user "1", who is the organizer
            userProperties.put("userId", "1"); 
            userProperties.put("reunionId", "101");

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "invitedUser");

            // Mock: Fetch reunion details (user "1" is organizer)
            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true); // Reunion found
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1); // Inviter "1" is organizer

            // Mock: Fetch invitedUser's ID (user "invitedUser" is ID 2)
            PreparedStatement MOCK_invitedUserPs = mock(PreparedStatement.class); // Separate mock for this specific query
            ResultSet MOCK_invitedUserRs = mock(ResultSet.class); // Separate mock for this result set
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(MOCK_invitedUserPs);
            when(MOCK_invitedUserPs.executeQuery()).thenReturn(MOCK_invitedUserRs);
            when(MOCK_invitedUserRs.next()).thenReturn(true); // User "invitedUser" found
            when(MOCK_invitedUserRs.getInt("id")).thenReturn(2); // Their ID is 2

            // Mock: Check if already participating (user 2 is NOT in reunion 101 yet)
            PreparedStatement MOCK_participationPs = mock(PreparedStatement.class);
            ResultSet MOCK_participationRs = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT * FROM participation"))).thenReturn(MOCK_participationPs);
            when(MOCK_participationPs.executeQuery()).thenReturn(MOCK_participationRs);
            when(MOCK_participationRs.next()).thenReturn(false); // Not yet participating

            // Mock: Insert into participation
            PreparedStatement MOCK_insertParticipationPs = mock(PreparedStatement.class);
            when(mockConnection.prepareStatement(startsWith("INSERT INTO participation"))).thenReturn(MOCK_insertParticipationPs);
            when(MOCK_insertParticipationPs.executeUpdate()).thenReturn(1);


            // Execute
            reunionService.execute(inviteJson, mockSession);

            // Verify: participation table insert
            verify(MOCK_insertParticipationPs, times(1)).executeUpdate();

            // Verify: success response sent to inviter
            ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockBasicRemote).sendText(responseCaptor.capture());
            JSONObject response = new JSONObject(responseCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertTrue(response.getBoolean("success"));
            // The message from ReunionService is "'username' has been successfully invited to the reunion."
            assertEquals("invitedUser has been successfully invited to the reunion.", response.getString("message"));
        }
    }
    
    // Test "inviterMembre" action - Failure: Inviter is not organizer
     @Test
     void testInviterMembre_FailNotOrganizer() throws Exception {
         try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
             dbStaticMock.when(Database::getInstance).thenReturn(mockDatabase);

             userProperties.put("userId", "2"); // User "2" is NOT the organizer
             userProperties.put("reunionId", "101");

             JSONObject inviteJson = new JSONObject();
             inviteJson.put("action", "inviterMembre");
             inviteJson.put("reunionId", "101");
             inviteJson.put("usernameToInvite", "anotherUser");

             // Mock: Fetch reunion details (organizer is user "1")
             when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
             when(mockResultSet.next()).thenReturn(true); // Reunion found
             when(mockResultSet.getString("type")).thenReturn("STANDARD");
             when(mockResultSet.getInt("organisateur_id")).thenReturn(1); // Organizer is "1"

             // Execute
             reunionService.execute(inviteJson, mockSession);

             // Verify: error response sent
             ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
             verify(mockBasicRemote).sendText(responseCaptor.capture());
             JSONObject response = new JSONObject(responseCaptor.getValue());
             assertEquals("invitationResult", response.getString("type"));
             assertFalse(response.getBoolean("success"));
             // Message updated to match exact string from ReunionService
             assertEquals("Only the organizer can invite members to this reunion.", response.getString("message"));
         }
     }

    @Test
    void testInviterMembre_Fail_UserToInviteNotFound() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getInstance).thenReturn(mockDatabase);

            userProperties.put("userId", "1"); // Organizer
            userProperties.put("reunionId", "101");

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "nonExistentUser");

            // Mock: Fetch reunion details
            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1);

            // Mock: Fetch invitedUser's ID (user not found)
            PreparedStatement MOCK_invitedUserPs = mock(PreparedStatement.class);
            ResultSet MOCK_invitedUserRs = mock(ResultSet.class); // Separate mock for this result set
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(MOCK_invitedUserPs);
            when(MOCK_invitedUserPs.executeQuery()).thenReturn(MOCK_invitedUserRs);
            when(MOCK_invitedUserRs.next()).thenReturn(false); // User "nonExistentUser" not found

            reunionService.execute(inviteJson, mockSession);

            ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockBasicRemote).sendText(responseCaptor.capture());
            JSONObject response = new JSONObject(responseCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertFalse(response.getBoolean("success"));
            assertEquals("User 'nonExistentUser' not found.", response.getString("message"));
        }
    }

    @Test
    void testInviterMembre_Fail_AlreadyParticipating() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getInstance).thenReturn(mockDatabase);

            userProperties.put("userId", "1"); // Organizer
            userProperties.put("reunionId", "101");

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "alreadyInUser");

            // Mock: Fetch reunion details
            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true); // Reunion found
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1);

            // Mock: Fetch invitedUser's ID (user "alreadyInUser" is ID 3)
            PreparedStatement MOCK_invitedUserPs = mock(PreparedStatement.class);
            ResultSet MOCK_invitedUserRs = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(MOCK_invitedUserPs);
            when(MOCK_invitedUserPs.executeQuery()).thenReturn(MOCK_invitedUserRs);
            when(MOCK_invitedUserRs.next()).thenReturn(true); // User "alreadyInUser" found
            when(MOCK_invitedUserRs.getInt("id")).thenReturn(3);

            // Mock: Check if already participating (user 3 IS in reunion 101)
            PreparedStatement MOCK_participationPs = mock(PreparedStatement.class);
            ResultSet MOCK_participationRs = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT * FROM participation"))).thenReturn(MOCK_participationPs);
            when(MOCK_participationPs.executeQuery()).thenReturn(MOCK_participationRs);
            when(MOCK_participationRs.next()).thenReturn(true); // Already participating

            reunionService.execute(inviteJson, mockSession);

            ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockBasicRemote).sendText(responseCaptor.capture());
            JSONObject response = new JSONObject(responseCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertFalse(response.getBoolean("success"));
            assertEquals("'alreadyInUser' is already a participant in this reunion.", response.getString("message"));
        }
    }

    // Add more tests:
    // - testEnvoyerMessage_Fail_UserNotFound (if sender name lookup fails)
    // - testInviterMembre_Success_PrivateReunion (check autorisation_reunion_privee insert)
    // - testInviterMembre_Fail_ReunionNotFound
    // - Test various error conditions like SQLException during DB operations.
}
