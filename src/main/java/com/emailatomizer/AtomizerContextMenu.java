package com.emailatomizer;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public final class AtomizerContextMenu implements ContextMenuItemsProvider {
    private final AtomizerState state;

    public AtomizerContextMenu(AtomizerState state) {
        this.state = state;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (selected == null || selected.isEmpty() || selected.get(0).request() == null) {
            return Collections.emptyList();
        }

        HttpRequest requestCandidate;
        try {
            requestCandidate = selected.get(0).request().copyToTempFile();
        } catch (Throwable t) {
            requestCandidate = selected.get(0).request();
        }
        final HttpRequest request = requestCandidate;
        JMenu menu = new JMenu("Email Atomizer");

        JMenuItem send = new JMenuItem("Send to Email Atomizer");
        send.addActionListener(e -> state.sendToBuilder(request));
        menu.add(send);

        return List.of(menu);
    }
}
