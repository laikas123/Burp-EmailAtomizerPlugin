package burp.api.montoya.intruder;
import burp.api.montoya.http.message.requests.HttpRequest;
public interface Intruder { void sendToIntruder(HttpRequest request); }
