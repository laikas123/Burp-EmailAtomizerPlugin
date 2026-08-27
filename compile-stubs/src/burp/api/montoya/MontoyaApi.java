package burp.api.montoya;
import burp.api.montoya.extension.Extension;
import burp.api.montoya.http.Http;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.repeater.Repeater;
import burp.api.montoya.intruder.Intruder;
import burp.api.montoya.organizer.Organizer;
public interface MontoyaApi {
 Extension extension(); Http http(); UserInterface userInterface(); Collaborator collaborator(); Logging logging();
 Repeater repeater(); Intruder intruder(); Organizer organizer();
}
