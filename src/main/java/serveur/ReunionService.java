package serveur;

import org.json.JSONObject;

import javax.websocket.Session;
import java.io.IOException;

public class ReunionService implements WebSocketAction {
    @Override
    public void execute(JSONObject data, Session session) throws IOException {
        String action = data.optString("action");
        String response;
        switch (action){

        }
    }


}
