package burp.api.montoya.collaborator;
import java.util.List;
public interface CollaboratorClient {
    CollaboratorPayload generatePayload(String customData, PayloadOption... options);
    List<Interaction> getAllInteractions();
}
