package burp.api.montoya.ui;
import burp.api.montoya.core.Registration;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import java.awt.Component;
public interface UserInterface {
 void applyThemeToComponent(Component component);
 Registration registerSuiteTab(String title, Component component);
 Registration registerContextMenuItemsProvider(ContextMenuItemsProvider provider);
 HttpRequestEditor createHttpRequestEditor(EditorOptions... options);
 HttpResponseEditor createHttpResponseEditor(EditorOptions... options);
}
