package burp.api.montoya.http;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
public interface Http { Registration registerHttpHandler(HttpHandler handler); HttpRequestResponse sendRequest(HttpRequest request); }
