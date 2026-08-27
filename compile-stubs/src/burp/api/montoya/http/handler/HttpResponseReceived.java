package burp.api.montoya.http.handler;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
public interface HttpResponseReceived extends HttpResponse { HttpRequest initiatingRequest(); Annotations annotations(); }
