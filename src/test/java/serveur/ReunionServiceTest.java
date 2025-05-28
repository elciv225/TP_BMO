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
    @Mock
    private Map<String, Object> mockUserProperties; // Mock the user properties map directly

    @InjectMocks
    private ReunionService reunionService;

    @Captor
    private ArgumentCaptor<String> stringArgumentCaptor;


    @BeforeEach
    void setUp() throws SQLException, IOException {
        // Common mock setups
        lenient().when(mockSession.getBasicRemote()).thenReturn(mockBasicRemote);
        lenient().when(mockSession.isOpen()).thenReturn(true);
        lenient().when(mockSession.getUserProperties()).thenReturn(mockUserProperties); // Use the mocked map

        // Default behavior for prepared statements that are commonly used
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        lenient().when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
        lenient().when(mockPreparedStatement.executeUpdate()).thenReturn(1); // Simulate 1 row affected for inserts/updates
        lenient().when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet); // Simulate returning a ResultSet
    }

    @Test
    void testCreerReunion_Success() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);

            JSONObject data = new JSONObject();
            data.put("action", "creation");
            data.put("nom", "Test Reunion");
            data.put("idOrganisateur", 1);
            data.put("debut", LocalDateTime.now().plusHours(1).toString()); // Ensure future date
            data.put("duree", 60);
            data.put("type", "STANDARD");
            // sujet and agenda are optional

            // Mock PreparedStatement for inserting reunion (to get generated ID)
            when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getInt(1)).thenReturn(100); // Mocked reunion ID

            // Mock PreparedStatement for inserting participation
            PreparedStatement mockPsParticipation = mock(PreparedStatement.class);
            when(mockConnection.prepareStatement(startsWith("INSERT INTO participation"))).thenReturn(mockPsParticipation);
            when(mockPsParticipation.executeUpdate()).thenReturn(1);

            reunionService.execute(data, mockSession);

            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            String response = stringArgumentCaptor.getValue();
            JSONObject jsonResponse = new JSONObject(response);

            assertEquals("reunion", jsonResponse.getString("modele"));
            assertEquals("reponseCreation", jsonResponse.getString("action"));
            assertEquals("succes", jsonResponse.getString("statut"));
            assertTrue(jsonResponse.has("reunion"), "Response should contain reunion details");
            assertEquals(100, jsonResponse.getJSONObject("reunion").getInt("id"), "Reunion ID should match mocked ID");
            assertTrue(jsonResponse.getBoolean("autoJoin"), "autoJoin should be true for successful creation");
        }
    }

    @Test
    void testCreerReunion_MissingNom() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);
            JSONObject data = new JSONObject();
            data.put("action", "creation");
            // "nom" is missing
            data.put("idOrganisateur", 1);
            // Other fields that might be required by ReunionManager for default values
            data.put("debut", LocalDateTime.now().plusHours(1).toString());
            data.put("duree", 60);
            data.put("type", "STANDARD");


            reunionService.execute(data, mockSession);

            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            String response = stringArgumentCaptor.getValue();
            JSONObject jsonResponse = new JSONObject(response);

            assertEquals("reunion", jsonResponse.getString("modele"));
            assertEquals("reponseCreation", jsonResponse.getString("action"));
            assertEquals("echec", jsonResponse.getString("statut"));
            assertTrue(jsonResponse.getString("message").contains("Nom et organisateur obligatoires"));
        }
    }


    @Test
    void testEnvoyerMessage_Success() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class);
             MockedStatic<ServeurWebSocket> wsStaticMock = Mockito.mockStatic(ServeurWebSocket.class)) {
            
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);
            
            // Prepare JSON input
            JSONObject messageJson = new JSONObject();
            messageJson.put("action", "envoyerMessage");
            messageJson.put("reunionId", "101");
            messageJson.put("userId", "1");
            messageJson.put("contenu", "Hello World");

            // Mock sender's session properties
            when(mockUserProperties.get("reunionId")).thenReturn("101");
            when(mockUserProperties.get("userId")).thenReturn("1");


            // Mock database checks: user exists, reunion exists, user is participant
            // 1. Check User & Get Name
            PreparedStatement mockPsCheckUser = mock(PreparedStatement.class);
            ResultSet mockRsCheckUser = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT CONCAT(nom, ' ', prenom) as nom_complet"))).thenReturn(mockPsCheckUser);
            when(mockPsCheckUser.executeQuery()).thenReturn(mockRsCheckUser);
            when(mockRsCheckUser.next()).thenReturn(true); 
            when(mockRsCheckUser.getString("nom_complet")).thenReturn("Test User");

            // 2. Check Reunion
            PreparedStatement mockPsCheckReunion = mock(PreparedStatement.class);
            ResultSet mockRsCheckReunion = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT COUNT(*) FROM reunion"))).thenReturn(mockPsCheckReunion);
            when(mockPsCheckReunion.executeQuery()).thenReturn(mockRsCheckReunion);
            when(mockRsCheckReunion.next()).thenReturn(true);
            when(mockRsCheckReunion.getInt(1)).thenReturn(1); // Reunion exists

            // 3. Check Participation
            PreparedStatement mockPsCheckPart = mock(PreparedStatement.class);
            ResultSet mockRsCheckPart = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT COUNT(*) FROM participation"))).thenReturn(mockPsCheckPart);
            when(mockPsCheckPart.executeQuery()).thenReturn(mockRsCheckPart);
            when(mockRsCheckPart.next()).thenReturn(true);
            when(mockRsCheckPart.getInt(1)).thenReturn(1); // User is participant

            // 4. Message Insert
            PreparedStatement mockPsInsertMsg = mock(PreparedStatement.class);
            when(mockConnection.prepareStatement(startsWith("INSERT INTO message"))).thenReturn(mockPsInsertMsg);
            when(mockPsInsertMsg.executeUpdate()).thenReturn(1);

            // Mock ServeurWebSocket.getSessions() for broadcast
            Set<Session> mockSessions = new HashSet<>();
            Session mockParticipantSession = mock(Session.class); // Another participant
            RemoteEndpoint.Basic mockParticipantRemote = mock(RemoteEndpoint.Basic.class);
            Map<String, Object> participantProps = new HashMap<>();
            participantProps.put("reunionId", "101");

            when(mockParticipantSession.isOpen()).thenReturn(true);
            when(mockParticipantSession.getBasicRemote()).thenReturn(mockParticipantRemote);
            when(mockParticipantSession.getUserProperties()).thenReturn(participantProps);
            
            mockSessions.add(mockSession); // Sender's session
            mockSessions.add(mockParticipantSession); // Other participant's session
            wsStaticMock.when(ServeurWebSocket::getSessions).thenReturn(mockSessions);

            reunionService.execute(messageJson, mockSession);

            // Verify broadcast to both sessions (sender and other participant)
            ArgumentCaptor<String> broadcastCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockBasicRemote, times(1)).sendText(broadcastCaptor.capture()); // Sender receives their own message back
            verify(mockParticipantRemote, times(1)).sendText(broadcastCaptor.capture()); // Other participant receives
            
            for(String capturedJsonString : broadcastCaptor.getAllValues()){
                JSONObject broadcastJson = new JSONObject(capturedJsonString);
                assertEquals("newMessage", broadcastJson.getString("type"));
                assertEquals("101", broadcastJson.getString("reunionId"));
                assertEquals("Test User", broadcastJson.getString("sender"));
                assertEquals("Hello World", broadcastJson.getString("content"));
                assertEquals("1", broadcastJson.getString("userId"));
            }
            
            // Verify message was inserted
            verify(mockPsInsertMsg).executeUpdate();
        }
    }

    @Test
    void testEnvoyerMessage_UserNotParticipant() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);

            JSONObject data = new JSONObject();
            data.put("action", "envoyerMessage");
            data.put("reunionId", "11");
            data.put("userId", "2");
            data.put("contenu", "Trying to talk");

            // Mock session properties for the sender
            when(mockUserProperties.get("reunionId")).thenReturn("11"); 
            when(mockUserProperties.get("userId")).thenReturn("2");


            // 1. Check User & Get Name
            PreparedStatement mockPsCheckUser = mock(PreparedStatement.class);
            ResultSet mockRsCheckUser = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT CONCAT(nom, ' ', prenom) as nom_complet"))).thenReturn(mockPsCheckUser);
            when(mockPsCheckUser.executeQuery()).thenReturn(mockRsCheckUser);
            when(mockRsCheckUser.next()).thenReturn(true); 
            when(mockRsCheckUser.getString("nom_complet")).thenReturn("Non Participant");

            // 2. Check Reunion
            PreparedStatement mockPsCheckReunion = mock(PreparedStatement.class);
            ResultSet mockRsCheckReunion = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT COUNT(*) FROM reunion"))).thenReturn(mockPsCheckReunion);
            when(mockPsCheckReunion.executeQuery()).thenReturn(mockRsCheckReunion);
            when(mockRsCheckReunion.next()).thenReturn(true);
            when(mockRsCheckReunion.getInt(1)).thenReturn(1); // Reunion exists

            // 3. Check Participation - User is NOT a participant
            PreparedStatement mockPsCheckPart = mock(PreparedStatement.class);
            ResultSet mockRsCheckPart = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT COUNT(*) FROM participation"))).thenReturn(mockPsCheckPart);
            when(mockPsCheckPart.executeQuery()).thenReturn(mockRsCheckPart);
            when(mockRsCheckPart.next()).thenReturn(true);
            when(mockRsCheckPart.getInt(1)).thenReturn(0); // User is NOT participant

            reunionService.execute(data, mockSession);

            // Verify error response sent back to the calling session
            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            JSONObject errorResponse = new JSONObject(stringArgumentCaptor.getValue());
            assertEquals("error", errorResponse.getString("type"));
            assertEquals("Vous ne participez pas à cette réunion.", errorResponse.getString("message"));

            // Verify no message insertion or broadcast occurred
            verify(mockConnection, never()).prepareStatement(startsWith("INSERT INTO message"));
        }
    }

    // Test "inviterMembre" action - Successful invitation by organizer
    @Test
    void testInviterMembre_SuccessByOrganizer() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);

            when(mockUserProperties.get("userId")).thenReturn("1"); // Inviter is user "1", who is the organizer

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
            PreparedStatement mockPsFetchUser = mock(PreparedStatement.class);
            ResultSet mockRsFetchUser = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(mockPsFetchUser);
            when(mockPsFetchUser.executeQuery()).thenReturn(mockRsFetchUser);
            when(mockRsFetchUser.next()).thenReturn(true); 
            when(mockRsFetchUser.getInt("id")).thenReturn(2); 

            // Mock: Check if already participating (user 2 is NOT in reunion 101 yet)
            PreparedStatement mockPsCheckPart = mock(PreparedStatement.class);
            ResultSet mockRsCheckPart = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT * FROM participation"))).thenReturn(mockPsCheckPart);
            when(mockPsCheckPart.executeQuery()).thenReturn(mockRsCheckPart);
            when(mockRsCheckPart.next()).thenReturn(false); 

            // Mock: Insert into participation
            PreparedStatement mockPsInsertPart = mock(PreparedStatement.class);
            when(mockConnection.prepareStatement(startsWith("INSERT INTO participation"))).thenReturn(mockPsInsertPart);
            when(mockPsInsertPart.executeUpdate()).thenReturn(1);

            reunionService.execute(inviteJson, mockSession);

            verify(mockPsInsertPart).executeUpdate();
            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            JSONObject response = new JSONObject(stringArgumentCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertTrue(response.getBoolean("success"));
            assertEquals("'invitedUser' a été invité avec succès à la réunion.", response.getString("message"));
        }
    }
    
    // Test "inviterMembre" action - Failure: Inviter is not organizer
     @Test
     void testInviterMembre_FailNotOrganizer() throws Exception {
         try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);

            when(mockUserProperties.get("userId")).thenReturn("2"); // User "2" is NOT the organizer

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "anotherUser");

            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true); 
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1); // Organizer is "1"

            reunionService.execute(inviteJson, mockSession);

            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            JSONObject response = new JSONObject(stringArgumentCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertFalse(response.getBoolean("success"));
            assertEquals("Seul l'organisateur peut inviter des membres à cette réunion.", response.getString("message"));
         }
     }

    @Test
    void testInviterMembre_Fail_UserToInviteNotFound() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);
            when(mockUserProperties.get("userId")).thenReturn("1"); // Organizer

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "nonExistentUser");

            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1);

            PreparedStatement mockPsFetchUser = mock(PreparedStatement.class);
            ResultSet mockRsFetchUser = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(mockPsFetchUser);
            when(mockPsFetchUser.executeQuery()).thenReturn(mockRsFetchUser);
            when(mockRsFetchUser.next()).thenReturn(false); // User "nonExistentUser" not found

            reunionService.execute(inviteJson, mockSession);

            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            JSONObject response = new JSONObject(stringArgumentCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertFalse(response.getBoolean("success"));
            assertEquals("Utilisateur 'nonExistentUser' non trouvé.", response.getString("message"));
        }
    }

    @Test
    void testInviterMembre_Fail_AlreadyParticipating() throws Exception {
        try (MockedStatic<Database> dbStaticMock = Mockito.mockStatic(Database.class)) {
            dbStaticMock.when(Database::getConnection).thenReturn(mockConnection);
            when(mockUserProperties.get("userId")).thenReturn("1"); // Organizer

            JSONObject inviteJson = new JSONObject();
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", "101");
            inviteJson.put("usernameToInvite", "alreadyInUser");

            when(mockConnection.prepareStatement(startsWith("SELECT type, organisateur_id FROM reunion"))).thenReturn(mockPreparedStatement);
            when(mockResultSet.next()).thenReturn(true); 
            when(mockResultSet.getString("type")).thenReturn("STANDARD");
            when(mockResultSet.getInt("organisateur_id")).thenReturn(1);

            PreparedStatement mockPsFetchUser = mock(PreparedStatement.class);
            ResultSet mockRsFetchUser = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT id FROM personne WHERE login = ?"))).thenReturn(mockPsFetchUser);
            when(mockPsFetchUser.executeQuery()).thenReturn(mockRsFetchUser);
            when(mockRsFetchUser.next()).thenReturn(true); 
            when(mockRsFetchUser.getInt("id")).thenReturn(3);

            PreparedStatement mockPsCheckPart = mock(PreparedStatement.class);
            ResultSet mockRsCheckPart = mock(ResultSet.class);
            when(mockConnection.prepareStatement(startsWith("SELECT * FROM participation"))).thenReturn(mockPsCheckPart);
            when(mockPsCheckPart.executeQuery()).thenReturn(mockRsCheckPart);
            when(mockRsCheckPart.next()).thenReturn(true); // Already participating

            reunionService.execute(inviteJson, mockSession);

            verify(mockBasicRemote).sendText(stringArgumentCaptor.capture());
            JSONObject response = new JSONObject(stringArgumentCaptor.getValue());
            assertEquals("invitationResult", response.getString("type"));
            assertFalse(response.getBoolean("success"));
            assertEquals("'alreadyInUser' participe déjà à cette réunion.", response.getString("message"));
        }
    }
}
