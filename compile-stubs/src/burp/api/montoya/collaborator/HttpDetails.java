package burp.api.montoya.collaborator;
import burp.api.montoya.http.HttpProtocol;
import burp.api.montoya.http.message.HttpRequestResponse;
public interface HttpDetails { HttpProtocol protocol(); HttpRequestResponse requestResponse(); }
