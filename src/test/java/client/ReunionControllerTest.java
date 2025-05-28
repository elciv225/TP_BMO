package client;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReunionControllerTest {

    @InjectMocks
    private ReunionController reunionController;

    @Mock
    private ClientWebSocket mockClientWebSocket;

    @Mock
    private TextField messageInput; // Mock FXML injected TextField

    @Captor
    private ArgumentCaptor<String> stringArgumentCaptor;

    private static boolean jfxIsSetup = false;

    @BeforeAll
    static void setupJavaFX() {
        // Initialize JavaFX Toolkit for tests that might indirectly use it (e.g., Platform.runLater).
        // This is a common workaround for running JavaFX dependent tests without a full application launch.
        if (!jfxIsSetup) {
            try {
                Platform.startup(() -> {}); // Initializes JavaFX environment
                jfxIsSetup = true;
                System.out.println("INFO: JavaFX Toolkit initialized for testing environment.");
            } catch (IllegalStateException e) {
                // Toolkit might have been initialized by another test class or a previous run.
                jfxIsSetup = true;
                System.out.println("INFO: JavaFX Toolkit was already initialized.");
            }
        }
    }
    
    @AfterAll
    static void tearDownJavaFX() {
        if (jfxIsSetup) {
            // Platform.exit(); // Generally not recommended to call Platform.exit() in tests
                               // as it can only be called once per JVM lifetime and may affect other test suites.
            // System.out.println("INFO: JavaFX Toolkit shutdown is typically managed by the JVM on exit.");
        }
    }


    @BeforeEach
    void setUp() throws Exception {
        // @InjectMocks handles injection of @Mock fields into reunionController if constructor/setter injection is possible.
        // For @FXML fields, manual injection via reflection is often necessary in unit tests.

        // Manually inject the mocked TextField for @FXML messageInput
        Field messageInputField = ReunionController.class.getDeclaredField("messageInput");
        messageInputField.setAccessible(true);
        messageInputField.set(reunionController, messageInput);

        // Manually set internal state fields of ReunionController required for envoyerMessage tests.
        // This avoids calling full initData() which might have other side effects or UI dependencies.
        setInternalState(reunionController, "currentReunionId", "testReunion123");
        setInternalState(reunionController, "currentUserId", 123);
        setInternalState(reunionController, "currentUserName", "Test User");
        setInternalState(reunionController, "clientWebSocket", mockClientWebSocket);
        setInternalState(reunionController, "isInitialized", true);
    }

    // Helper method to set private/protected fields using reflection
    private void setInternalState(Object target, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testEnvoyerMessage_EmptyMessage_ShouldNotSend() {
        when(messageInput.getText()).thenReturn(""); // Simulate empty message input

        reunionController.envoyerMessage();

        verify(mockClientWebSocket, never()).envoyerRequete(anyString());
    }

    @Test
    void testEnvoyerMessage_ValidMessage_ConstructsCorrectJsonAndSends() {
        String testMessage = "Hello, WhatsApp!";
        when(messageInput.getText()).thenReturn(testMessage);
        when(mockClientWebSocket.isConnected()).thenReturn(true); // Assume WebSocket is connected

        reunionController.envoyerMessage();

        verify(mockClientWebSocket, times(1)).envoyerRequete(stringArgumentCaptor.capture());
        String jsonSent = stringArgumentCaptor.getValue();
        assertNotNull(jsonSent, "JSON string sent should not be null");

        JSONObject jsonObject = new JSONObject(jsonSent);
        assertEquals("reunion", jsonObject.getString("modele"), "JSON 'modele' should be 'reunion'");
        assertEquals("envoyerMessage", jsonObject.getString("action"), "JSON 'action' should be 'envoyerMessage'");
        assertEquals("testReunion123", jsonObject.getString("reunionId"), "JSON 'reunionId' is incorrect");
        assertEquals("123", jsonObject.getString("userId"), "JSON 'userId' is incorrect (should be stringified int)");
        assertEquals(testMessage, jsonObject.getString("contenu"), "JSON 'contenu' is incorrect");

        verify(messageInput, times(1)).clear(); // Input field should be cleared after sending
    }
    
    @Test
    void testEnvoyerMessage_ControllerNotInitialized_ShouldNotSend() {
        // Override isInitialized to false for this specific test
        try {
            setInternalState(reunionController, "isInitialized", false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set isInitialized for test", e);
        }

        when(messageInput.getText()).thenReturn("A message that won't be sent");
        // Note: Verification of showAlert call is omitted for simplicity in pure unit test.
        // In a UI test, one would verify the alert.
        
        reunionController.envoyerMessage();
        
        verify(mockClientWebSocket, never()).envoyerRequete(anyString());
    }
    
    @Test
    void testEnvoyerMessage_WebSocketNotConnected_ShouldNotSend() {
        when(messageInput.getText()).thenReturn("Another message not to be sent");
        when(mockClientWebSocket.isConnected()).thenReturn(false); // Simulate WebSocket not connected
        // Note: Verification of showAlert call is omitted.
        
        reunionController.envoyerMessage();
        
        verify(mockClientWebSocket, never()).envoyerRequete(anyString());
    }

}
