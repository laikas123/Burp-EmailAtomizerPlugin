package com.emailatomizer;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public final class Extension implements BurpExtension {
    private AtomizerState state;
    private CollaboratorPoller poller;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Email Atomizer");

        state = new AtomizerState(api);
        AtomizerPanel panel = new AtomizerPanel(state, api);
        state.setPanel(panel);
        state.initializeCollaborator();

        api.userInterface().applyThemeToComponent(panel);
        api.userInterface().registerSuiteTab("Email Atomizer", panel);
        api.userInterface().registerContextMenuItemsProvider(new AtomizerContextMenu(state));
        api.http().registerHttpHandler(new AtomizerHttpHandler(state, panel));

        poller = new CollaboratorPoller(api, state);
        state.setCollaboratorPoller(poller);
        poller.start();

        api.extension().registerUnloadingHandler(() -> {
            if (poller != null) poller.shutdown();
            if (state != null) state.shutdown();
        });

        api.logging().logToOutput("Email Atomizer v0.3.8 loaded. Mutation cases: " + state.mutations().size());
    }
}
