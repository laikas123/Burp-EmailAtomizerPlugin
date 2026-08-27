package burp.api.montoya.http.handler;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.message.requests.HttpRequest;
public interface RequestToBeSentAction {
    static RequestToBeSentAction continueWith(HttpRequest request) { return null; }
    static RequestToBeSentAction continueWith(HttpRequest request, Annotations annotations) { return null; }
}
