package burp.api.montoya.extension;
import burp.api.montoya.core.Registration;
public interface Extension {
    void setName(String name);
    Registration registerUnloadingHandler(ExtensionUnloadingHandler handler);
}
