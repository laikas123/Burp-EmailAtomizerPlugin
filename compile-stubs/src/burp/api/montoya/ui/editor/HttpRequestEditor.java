package burp.api.montoya.ui.editor;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.awt.Component;
public interface HttpRequestEditor { HttpRequest getRequest(); void setRequest(HttpRequest request); Component uiComponent(); }
