package burp.api.montoya.http.handler;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.message.requests.HttpRequest;
public interface HttpRequestToBeSent extends HttpRequest { Annotations annotations(); }
