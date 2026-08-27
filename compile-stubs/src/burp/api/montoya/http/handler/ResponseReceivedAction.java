package burp.api.montoya.http.handler;
import burp.api.montoya.http.message.responses.HttpResponse;
public interface ResponseReceivedAction {
    static ResponseReceivedAction continueWith(HttpResponse response) { return null; }
}
