package serveur;

import org.json.JSONObject;
import javax.websocket.Session;
import java.io.IOException;

public interface WebSocketAction {
    void execute(JSONObject data, Session session) throws IOException;
}
