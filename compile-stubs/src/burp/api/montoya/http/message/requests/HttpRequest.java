package burp.api.montoya.http.message.requests;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.core.ByteArray;
public interface HttpRequest {
 boolean isInScope(); String method(); String url(); String bodyToString(); ByteArray body(); HttpService httpService();
 HttpRequest withBody(String body); HttpRequest withBody(ByteArray body); HttpRequest copyToTempFile();
 static HttpRequest httpRequest() { return null; }
 static HttpRequest httpRequest(String request) { return null; }
 static HttpRequest httpRequest(HttpService service, String request) { return null; }
 static HttpRequest httpRequest(HttpService service, ByteArray request) { return null; }
}
