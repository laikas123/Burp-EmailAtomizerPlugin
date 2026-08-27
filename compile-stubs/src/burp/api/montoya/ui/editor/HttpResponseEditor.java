package burp.api.montoya.ui.editor;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.awt.Component;
public interface HttpResponseEditor { HttpResponse getResponse(); void setResponse(HttpResponse response); Component uiComponent(); }
