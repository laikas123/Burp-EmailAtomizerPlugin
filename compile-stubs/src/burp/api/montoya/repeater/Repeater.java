package burp.api.montoya.repeater;
import burp.api.montoya.http.message.requests.HttpRequest;
public interface Repeater { void sendToRepeater(HttpRequest request); }
