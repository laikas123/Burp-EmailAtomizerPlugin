package com.emailatomizer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AtomizerPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient AtomizerState state;

    // Global / live settings.
    private final JTextField canonical = new JTextField(34);
    private final JTextField fallbackDomain = new JTextField("collaborator.invalid", 26);
    private final JCheckBox liveEnabled = new JCheckBox("Enable live mutation", false);
    private final JCheckBox scopeOnly = new JCheckBox("Only send/mutate Burp-scope requests", true);
    private final JCheckBox skipGet = new JCheckBox("Skip GET/HEAD in live mode", true);
    private final JComboBox<MutationCase> liveMutation;
    private final JTextArea mutationDescription = new JTextArea(4, 70);

    // Passive discovery.
    private final JCheckBox passiveEnabled = new JCheckBox("Passive email discovery", true);
    private final JCheckBox passiveScopeOnly = new JCheckBox("Only observe Burp-scope requests", true);
    private final JCheckBox passiveSkipGet = new JCheckBox("Skip GET/HEAD discovery", false);
    private final DiscoveryTableModel discoveryModel;
    private final JTable discoveryTable;
    private final transient HttpRequestEditor discoveryRequestEditor;
    private final transient HttpResponseEditor discoveryResponseEditor;

    // Test builder.
    private volatile HttpRequest builderRequest;
    private final JLabel builderRequestLabel = new JLabel("No request loaded. Right-click a Burp request and choose Send to Email Atomizer.");
    private final JComboBox<EmailFieldChoice> builderCandidates = new JComboBox<>();
    private final JComboBox<EmailFieldChoice> receiverCandidates = new JComboBox<>();
    private final JTextField controlledReceiver = new JTextField("", 34);
    private final JRadioButton receiverCollaborator = new JRadioButton("Burp Collaborator (recommended)", true);
    private final JRadioButton receiverControlled = new JRadioButton("Email address I control", false);
    private final JTextArea scenarioSummary = new JTextArea(5, 72);
    private final Map<String, JCheckBox> familyChecks = new LinkedHashMap<>();
    private final JCheckBox includeProbeOnly = new JCheckBox("Include non-receiver/probe-only cases", true);
    private final JCheckBox includeCustomMutations = new JCheckBox("Include custom pasted mutations", true);
    private final JTextArea customMutations = new JTextArea(8, 56);
    private final JSpinner delay = new JSpinner(new SpinnerNumberModel(1000, 0, 60000, 250));
    private final JSpinner collaboratorWindow = new JSpinner(new SpinnerNumberModel(3000, 0, 30000, 500));
    private final JSpinner maxTests = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
    private final JTextField stopStatusCodes = new JTextField("420,429", 12);
    private final JTextArea stopResponseText = new JTextArea("tooManyAttempts", 3, 26);
    private final JCheckBox deliverySentinelsEnabled = new JCheckBox("Enable delivery-path sentinels", false);
    private final JSpinner deliverySentinelEvery = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
    private final JCheckBox respectRetryAfter = new JCheckBox("Respect Retry-After if continuing (opt-in)", false);
    private final JSpinner fallback429Delay = new JSpinner(new SpinnerNumberModel(10000, 0, 300000, 1000));
    private final JTextArea runLog = new JTextArea(7, 100);
    private final JLabel matrixCount = new JLabel();

    // Results.
    private final ResultTableModel resultModel;
    private final JTable resultTable;
    private final transient HttpRequestEditor resultRequestEditor;
    private final transient HttpResponseEditor resultResponseEditor;
    private final JTextArea collaboratorDetail = new JTextArea();
    private final CollaboratorAccountsPanel collaboratorAccountsPanel;
    private final JButton resumeMatrixButton = new JButton("Resume remaining tests");
    private final JButton retryStoppedCaseButton = new JButton("Retry stopped case + continue");
    private final JButton stopMatrixButton = new JButton("STOP current run");
    private final JButton stopMatrixResultsButton = new JButton("STOP current run");
    private final JLabel resumeStatus = new JLabel("No resumable run.");

    private final JLabel status = new JLabel("Ready. Passive discovery is enabled.");
    private final JTabbedPane tabs = new JTabbedPane();
    private JPanel discoveryPanel;
    private JPanel builderPanel;

    public AtomizerPanel(AtomizerState state, MontoyaApi api) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.liveMutation = new JComboBox<>(state.mutations().toArray(new MutationCase[0]));
        this.discoveryModel = new DiscoveryTableModel(state);
        this.discoveryTable = new JTable(discoveryModel);
        this.resultModel = new ResultTableModel(state);
        this.resultTable = new JTable(resultModel);
        this.discoveryRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.discoveryResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.resultRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.resultResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.collaboratorAccountsPanel = new CollaboratorAccountsPanel(
                state,
                this::setCanonicalEmail,
                this::setControlledReceiver);

        canonical.setToolTipText("Email value currently selected for testing. Sending a request to Atomizer fills this automatically.");
        fallbackDomain.setToolTipText("Used only when Burp Collaborator is unavailable.");
        mutationDescription.setEditable(false);
        mutationDescription.setLineWrap(true);
        mutationDescription.setWrapStyleWord(true);
        mutationDescription.setOpaque(false);
        configureHttpDetailArea(collaboratorDetail, "Select a result row to inspect its Collaborator interactions.");
        resultRequestEditor.setRequest(HttpRequest.httpRequest());
        resultResponseEditor.setResponse(HttpResponse.httpResponse());
        discoveryRequestEditor.setRequest(HttpRequest.httpRequest());
        discoveryResponseEditor.setResponse(HttpResponse.httpResponse());
        resumeMatrixButton.setEnabled(false);
        retryStoppedCaseButton.setEnabled(false);
        stopMatrixButton.setEnabled(false);
        stopMatrixResultsButton.setEnabled(false);

        buildGlobalBar();
        discoveryPanel = buildDiscoveryPanel();
        builderPanel = buildBuilderPanel();
        tabs.addTab("Discoveries", discoveryPanel);
        tabs.addTab("Test Builder", builderPanel);
        tabs.addTab("Mutation Strings", new MutationStringsPanel(state, this::canonicalEmail, this::fallbackDomain));
        tabs.addTab("Results", buildResultsPanel());
        tabs.addTab("Collaborator Accounts", collaboratorAccountsPanel);
        tabs.addTab("Live Mode", buildLivePanel());

        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        liveMutation.addActionListener(e -> updateMutationDescription());
        builderCandidates.addActionListener(e -> candidateChanged());
        receiverCandidates.addActionListener(e -> updateScenarioControls());
        receiverCollaborator.addActionListener(e -> updateScenarioControls());
        receiverControlled.addActionListener(e -> updateScenarioControls());
        controlledReceiver.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateScenarioControls(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateScenarioControls(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateScenarioControls(); }
        });
        controlledReceiver.setToolTipText("Used when 'Email address I control' is selected. Receiver-directed probes use this mailbox instead of Collaborator.");
        respectRetryAfter.setToolTipText("Off by default. When enabled, Atomizer enforces the server's Retry-After before Resume/Retry. Uncheck at any time to manage cooldowns manually, including for an existing checkpoint.");
        receiverCandidates.setToolTipText("Optional second email occurrence in the same HTTP request. Choose None to change only the primary occurrence.");
        scenarioSummary.setEditable(false);
        scenarioSummary.setLineWrap(true);
        scenarioSummary.setWrapStyleWord(true);
        scenarioSummary.setOpaque(false);
        updateMutationDescription();
        selectConservativePreset();
        updateMatrixCount();
    }

    private void buildGlobalBar() {
        JPanel global = new JPanel(new GridBagLayout());
        global.setBorder(BorderFactory.createTitledBorder("Email Atomizer v0.3.8"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;

        addRow(global, c, 0, "Selected/canonical email", canonical);
        addRow(global, c, 1, "Fallback OAST domain", fallbackDomain);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1;
        JPanel safety = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        safety.add(scopeOnly);
        global.add(safety, c);
        add(global, BorderLayout.NORTH);
    }

    private JPanel buildDiscoveryPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton send = new JButton("Send selected to Test Builder");
        JButton clear = new JButton("Clear discoveries");
        controls.add(passiveEnabled);
        controls.add(passiveScopeOnly);
        controls.add(passiveSkipGet);
        controls.add(send);
        controls.add(clear);

        discoveryTable.setAutoCreateRowSorter(true);
        discoveryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        discoveryTable.setFillsViewportHeight(true);
        discoveryTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        discoveryTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        discoveryTable.getColumnModel().getColumn(5).setPreferredWidth(170);
        discoveryTable.getColumnModel().getColumn(6).setPreferredWidth(260);
        discoveryTable.getColumnModel().getColumn(9).setPreferredWidth(260);
        discoveryTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedDiscoveryDetails();
        });
        discoveryTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) sendSelectedDiscovery();
            }
            @Override public void mousePressed(MouseEvent e) { maybeShowDiscoveryPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowDiscoveryPopup(e); }
        });

        send.addActionListener(e -> sendSelectedDiscovery());
        clear.addActionListener(e -> {
            state.clearDiscoveries();
            discoveryRequestEditor.setRequest(HttpRequest.httpRequest());
            discoveryResponseEditor.setResponse(HttpResponse.httpResponse());
        });

        JTabbedPane details = new JTabbedPane();
        details.addTab("Request", discoveryRequestEditor.uiComponent());
        details.addTab("Response", discoveryResponseEditor.uiComponent());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(discoveryTable), details);
        split.setResizeWeight(0.62);
        split.setDividerLocation(360);
        split.setOneTouchExpandable(true);

        JTextArea help = new JTextArea(
                "Browse normally. Atomizer passively records requests containing email-like values without modifying or replaying them. " +
                "Select a row to inspect its request/response. Double-click to load Test Builder; right-click rows or Burp-native message editors for send-to actions.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        panel.add(controls, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(help, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBuilderPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel request = new JPanel(new GridBagLayout());
        request.setBorder(BorderFactory.createTitledBorder("Loaded request"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        addRow(request, c, 0, "Request", builderRequestLabel);
        addRow(request, c, 1, "Primary email occurrence to test", builderCandidates);
        addRow(request, c, 2, "Additional receiver occurrence (optional)", receiverCandidates);

        ButtonGroup receiverGroup = new ButtonGroup();
        receiverGroup.add(receiverCollaborator);
        receiverGroup.add(receiverControlled);
        JPanel receiverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        receiverPanel.add(receiverCollaborator);
        receiverPanel.add(receiverControlled);
        receiverPanel.add(controlledReceiver);
        addRow(request, c, 3, "Alternate / receiver address used by tests", receiverPanel);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1;
        scenarioSummary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("What this run will do"),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        request.add(scenarioSummary, c);
        updateScenarioControls();

        JPanel families = new JPanel();
        families.setLayout(new BoxLayout(families, BoxLayout.Y_AXIS));
        families.setBorder(BorderFactory.createTitledBorder("Mutation matrices"));

        Map<String, int[]> counts = new LinkedHashMap<>();
        for (MutationCase m : state.mutations()) {
            int[] n = counts.computeIfAbsent(m.family(), ignored -> new int[2]);
            n[0]++;
            if (m.collaboratorCapable()) n[1]++;
        }
        for (String family : state.mutationFamilies()) {
            int[] n = counts.get(family);
            JCheckBox box = new JCheckBox(family + "  (" + n[0] + " tests, " + n[1] + " receiver-directed)");
            box.setActionCommand(family);
            box.addActionListener(e -> { updateMatrixCount(); updateScenarioControls(); });
            familyChecks.put(family, box);
            families.add(box);
        }

        JPanel presetButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton conservative = new JButton("Conservative");
        JButton full = new JButton("Full matrix");
        JButton oast = new JButton("Receiver-directed");
        JButton idna2008 = new JButton("IDNA2008 bounty");
        idna2008.setToolTipText("Select the curated IDNA2008/PVALID U-label, JSON-wire, and literal A-label candidate families.");
        JButton dual = new JButton("Two competing addresses");
        JButton baselineOnly = new JButton("Baseline only");
        JButton all = new JButton("Select all");
        JButton none = new JButton("Select none");
        conservative.addActionListener(e -> selectConservativePreset());
        full.addActionListener(e -> selectAllFamilies());
        oast.addActionListener(e -> selectOastFamilies());
        idna2008.addActionListener(e -> selectIdna2008Families());
        dual.addActionListener(e -> { selectDualAddressFamilies(); updateScenarioControls(); });
        baselineOnly.addActionListener(e -> selectBaselineOnlyPreset());
        all.addActionListener(e -> selectAllFamilies());
        none.addActionListener(e -> selectNoFamilies());
        presetButtons.add(conservative);
        presetButtons.add(full);
        presetButtons.add(oast);
        presetButtons.add(idna2008);
        presetButtons.add(dual);
        presetButtons.add(baselineOnly);
        presetButtons.add(all);
        presetButtons.add(none);
        families.add(presetButtons);
        includeProbeOnly.addActionListener(e -> updateMatrixCount());
        families.add(includeProbeOnly);

        customMutations.setLineWrap(false);
        customMutations.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        customMutations.setToolTipText("One mutation per line. Blank lines and # comments are ignored. Supports raw Unicode, \\uXXXX, and placeholders such as {EMAIL}, {DOMAIN}, {RECEIVER}.");
        customMutations.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateMatrixCount(); updateScenarioControls(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateMatrixCount(); updateScenarioControls(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateMatrixCount(); updateScenarioControls(); }
        });
        includeCustomMutations.addActionListener(e -> { updateMatrixCount(); updateScenarioControls(); });
        JPanel customPanel = new JPanel(new BorderLayout(4, 4));
        customPanel.setBorder(BorderFactory.createTitledBorder("Custom copy/paste mutation set (one payload per line)"));
        customPanel.add(includeCustomMutations, BorderLayout.NORTH);
        customPanel.add(new JScrollPane(customMutations), BorderLayout.CENTER);
        JTextArea customHelp = new JTextArea("Paste concrete email values or templates. Examples: poc@gm\\u1d43il.example.com   |   {LOCAL}@gm\\u1d43il.{DOMAIN}   |   {RECEIVER}. Lines beginning with # are comments.");
        customHelp.setEditable(false);
        customHelp.setLineWrap(true);
        customHelp.setWrapStyleWord(true);
        customHelp.setOpaque(false);
        customPanel.add(customHelp, BorderLayout.SOUTH);
        families.add(customPanel);

        JPanel rate = new JPanel(new GridBagLayout());
        rate.setBorder(BorderFactory.createTitledBorder("Rate limiting / run controls"));
        GridBagConstraints r = new GridBagConstraints();
        r.insets = new Insets(3, 4, 3, 4);
        r.anchor = GridBagConstraints.WEST;
        r.fill = GridBagConstraints.HORIZONTAL;
        addRow(rate, r, 0, "Minimum delay between requests (ms)", delay);
        addRow(rate, r, 1, "Collaborator collection window after OAST request (ms)", collaboratorWindow);
        addRow(rate, r, 2, "Maximum mutation tests (0 = all selected)", maxTests);
        addRow(rate, r, 3, "Stop on HTTP status codes", stopStatusCodes);

        stopResponseText.setLineWrap(true);
        stopResponseText.setWrapStyleWord(true);
        stopResponseText.setToolTipText("Case-insensitive response-body substrings, one per line. The default catches Instacart tooManyAttempts.");
        JScrollPane stopTextScroll = new JScrollPane(stopResponseText);
        stopTextScroll.setPreferredSize(new Dimension(320, 64));
        addRow(rate, r, 4, "Stop if response body contains", stopTextScroll);

        addRow(rate, r, 5, "Fallback rate-limit pause (ms)", fallback429Delay);
        addRow(rate, r, 6, "Delivery sentinel every N mutation tests", deliverySentinelEvery);
        deliverySentinelEvery.setToolTipText("When delivery sentinels are enabled, send a direct Collaborator control at the start and end, plus every N mutation tests. 0 = start/end only.");

        r.gridx = 0; r.gridy = 7; r.gridwidth = 2;
        JPanel rateChecks = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rateChecks.add(respectRetryAfter);
        rateChecks.add(deliverySentinelsEnabled);
        rate.add(rateChecks, r);

        JPanel action = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton run = new JButton("Run selected matrices");
        run.addActionListener(e -> runBuilderMatrix());
        stopMatrixButton.addActionListener(e -> state.cancelMatrix());
        stopMatrixButton.setToolTipText("Stop after the request currently in flight / collection window finishes.");
        action.add(run);
        action.add(stopMatrixButton);
        maxTests.addChangeListener(e -> updateMatrixCount());
        deliverySentinelsEnabled.addActionListener(e -> updateMatrixCount());
        deliverySentinelEvery.addChangeListener(e -> updateMatrixCount());
        action.add(matrixCount);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(families);
        center.add(rate);

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        request.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollContent.add(request);
        scrollContent.add(Box.createVerticalStrut(8));
        scrollContent.add(center);

        JScrollPane builderScroll = new JScrollPane(scrollContent,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        builderScroll.setBorder(null);
        builderScroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(builderScroll, BorderLayout.CENTER);
        panel.add(action, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clear = new JButton("Clear results");
        JButton copy = new JButton("Copy selected mutated email");
        JButton export = new JButton("Export CSV");
        clear.addActionListener(e -> {
            state.clearResults();
            resultRequestEditor.setRequest(HttpRequest.httpRequest());
            resultResponseEditor.setResponse(HttpResponse.httpResponse());
            collaboratorDetail.setText("");
        });
        copy.addActionListener(e -> copySelectedResult());
        export.addActionListener(e -> exportCsv());
        resumeMatrixButton.addActionListener(e -> state.resumeMatrix());
        retryStoppedCaseButton.addActionListener(e -> state.retryStoppedCaseAndResume());
        stopMatrixResultsButton.addActionListener(e -> state.cancelMatrix());
        stopMatrixResultsButton.setToolTipText("Stop after the request currently in flight / collection window finishes.");
        buttons.add(clear);
        buttons.add(copy);
        buttons.add(export);
        buttons.add(stopMatrixResultsButton);
        buttons.add(resumeMatrixButton);
        buttons.add(retryStoppedCaseButton);
        buttons.add(resumeStatus);

        resultTable.setAutoCreateRowSorter(true);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setFillsViewportHeight(true);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(145);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(105);
        resultTable.getColumnModel().getColumn(5).setPreferredWidth(320);
        resultTable.getColumnModel().getColumn(6).setPreferredWidth(330);
        resultTable.getColumnModel().getColumn(7).setPreferredWidth(330);
        resultTable.getColumnModel().getColumn(10).setPreferredWidth(160);
        resultTable.getColumnModel().getColumn(11).setPreferredWidth(155);
        resultTable.getColumnModel().getColumn(13).setPreferredWidth(240);
        resultTable.getColumnModel().getColumn(14).setPreferredWidth(280);
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedResultDetails();
        });
        resultTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) showSelectedResultDetails();
            }
            @Override public void mousePressed(MouseEvent e) { maybeShowResultPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowResultPopup(e); }
        });

        runLog.setEditable(false);
        runLog.setLineWrap(true);
        runLog.setWrapStyleWord(true);
        runLog.setText("Run log ready. If a matrix is blocked or fails, the reason will appear here and in Burp's extension output.\n");

        JTabbedPane details = new JTabbedPane();
        details.addTab("Request", resultRequestEditor.uiComponent());
        details.addTab("Response", resultResponseEditor.uiComponent());
        details.addTab("Collaborator", new JScrollPane(collaboratorDetail));
        details.addTab("Run log", new JScrollPane(runLog));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(resultTable), details);
        split.setResizeWeight(0.62);
        split.setDividerLocation(370);
        split.setOneTouchExpandable(true);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private static void configureHttpDetailArea(JTextArea area, String placeholder) {
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setText(placeholder);
        area.setCaretPosition(0);
    }

    private void showSelectedResultDetails() {
        AtomResult r = selectedResult();
        if (r == null) return;

        HttpRequest req = r.request;
        if (req == null && r.requestText != null && !r.requestText.isBlank()) {
            try { req = HttpRequest.httpRequest(r.requestText); } catch (Throwable ignored) {}
        }
        resultRequestEditor.setRequest(req == null ? HttpRequest.httpRequest() : req);

        HttpResponse resp = r.response;
        if (resp == null && r.responseText != null && !r.responseText.isBlank() && !r.responseText.startsWith("[No HTTP response")) {
            try { resp = HttpResponse.httpResponse(r.responseText); } catch (Throwable ignored) {}
        }
        resultResponseEditor.setResponse(resp == null ? HttpResponse.httpResponse() : resp);

        collaboratorDetail.setText(r.collaboratorTranscript());
        collaboratorDetail.setCaretPosition(0);
    }

    private AtomResult selectedResult() {
        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        List<AtomResult> rows = state.results();
        return modelRow < 0 || modelRow >= rows.size() ? null : rows.get(modelRow);
    }

    private EmailDiscovery selectedDiscovery() {
        int viewRow = discoveryTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = discoveryTable.convertRowIndexToModel(viewRow);
        return discoveryModel.row(modelRow);
    }

    private void showSelectedDiscoveryDetails() {
        EmailDiscovery d = selectedDiscovery();
        if (d == null) return;
        discoveryRequestEditor.setRequest(d.request == null ? HttpRequest.httpRequest() : d.request);
        discoveryResponseEditor.setResponse(d.response == null ? HttpResponse.httpResponse() : d.response);
    }

    private void maybeShowResultPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = resultTable.rowAtPoint(e.getPoint());
        if (row >= 0) resultTable.setRowSelectionInterval(row, row);
        AtomResult r = selectedResult();
        if (r == null || r.request == null) return;
        showRequestPopup(e.getComponent(), e.getX(), e.getY(), r.request, false);
    }

    private void maybeShowDiscoveryPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = discoveryTable.rowAtPoint(e.getPoint());
        if (row >= 0) discoveryTable.setRowSelectionInterval(row, row);
        EmailDiscovery d = selectedDiscovery();
        if (d == null || d.request == null) return;
        showRequestPopup(e.getComponent(), e.getX(), e.getY(), d.request, true);
    }

    private void showRequestPopup(Component component, int x, int y, HttpRequest request, boolean discovery) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem repeater = new JMenuItem("Send to Repeater");
        repeater.addActionListener(ev -> state.sendRequestToRepeater(request));
        menu.add(repeater);

        JMenuItem intruder = new JMenuItem("Send to Intruder");
        intruder.addActionListener(ev -> state.sendRequestToIntruder(request));
        menu.add(intruder);

        JMenuItem organizer = new JMenuItem("Send to Organizer");
        organizer.addActionListener(ev -> state.sendRequestToOrganizer(request));
        menu.add(organizer);

        menu.addSeparator();
        JMenuItem atomizer = new JMenuItem(discovery ? "Send to Email Atomizer Test Builder" : "Send result request to Email Atomizer Test Builder");
        atomizer.addActionListener(ev -> state.sendToBuilder(request));
        menu.add(atomizer);

        menu.show(component, x, y);
    }

    public void setRunActive(boolean active) {
        SwingUtilities.invokeLater(() -> {
            stopMatrixButton.setEnabled(active);
            stopMatrixResultsButton.setEnabled(active);
            stopMatrixButton.setText(active ? "STOP current run" : "STOP current run");
            stopMatrixResultsButton.setText(active ? "STOP current run" : "STOP current run");
        });
    }

    public void setResumeAvailable(boolean available, int remaining, String reason) {
        SwingUtilities.invokeLater(() -> {
            resumeMatrixButton.setEnabled(available);
            if (available) resumeStatus.setText(remaining + " remaining after " + reason);
            else if (!retryStoppedCaseButton.isEnabled()) resumeStatus.setText("No resumable run.");
        });
    }

    public void setRetryStopAvailable(boolean available, String mutationId, String reason) {
        SwingUtilities.invokeLater(() -> {
            retryStoppedCaseButton.setEnabled(available);
            retryStoppedCaseButton.setToolTipText(available
                    ? "Re-run " + mutationId + " (the request that hit " + reason + ") and then continue with unsent tests."
                    : null);
            if (available) {
                String remainingText = resumeMatrixButton.isEnabled() ? resumeStatus.getText() + "; " : "";
                resumeStatus.setText(remainingText + "retry available for " + mutationId + " after " + reason);
            } else if (!resumeMatrixButton.isEnabled()) {
                resumeStatus.setText("No resumable run.");
            }
        });
    }

    private JPanel buildLivePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 5, 4, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        addRow(panel, c, 0, "Live mutation", liveMutation);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 2; c.weightx = 1;
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checks.add(liveEnabled);
        checks.add(skipGet);
        panel.add(checks, c);

        c.gridy = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        panel.add(mutationDescription, c);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        bottom.add(status, BorderLayout.WEST);
        bottom.add(new JLabel("Passive → Send to Atomizer → choose matrices → Run"), BorderLayout.EAST);
        return bottom;
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row, String label, Component field) {
        c.gridwidth = 1;
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }

    private void sendSelectedDiscovery() {
        int viewRow = discoveryTable.getSelectedRow();
        if (viewRow < 0) {
            setStatus("Select a passive discovery first.");
            return;
        }
        int modelRow = discoveryTable.convertRowIndexToModel(viewRow);
        EmailDiscovery d = discoveryModel.row(modelRow);
        if (d != null) state.sendDiscoveryToBuilder(d);
    }

    public void loadIntoBuilder(HttpRequest request, List<EmailCandidate> candidates) {
        loadIntoBuilder(request, candidates, candidates.isEmpty() ? 0 : 1);
    }

    public void loadIntoBuilder(HttpRequest request, List<EmailCandidate> candidates, int preferredIndex) {
        SwingUtilities.invokeLater(() -> {
            builderRequest = request;
            String scopeNote = request.isInScope() ? "" : "  [OUT OF BURP SCOPE]";
            builderRequestLabel.setText(request.method() + "  " + request.url() + scopeNote);

            DefaultComboBoxModel<EmailFieldChoice> primaryModel = new DefaultComboBoxModel<>();
            DefaultComboBoxModel<EmailFieldChoice> receiverModel = new DefaultComboBoxModel<>();
            receiverModel.addElement(EmailFieldChoice.none());

            EmailFieldChoice preferred = null;
            int index = 1;
            for (EmailCandidate candidate : candidates) {
                EmailFieldChoice choice = new EmailFieldChoice(index++, candidate);
                primaryModel.addElement(choice);
                receiverModel.addElement(choice);
                if (preferred == null && choice.index() == preferredIndex) {
                    preferred = choice;
                }
            }
            builderCandidates.setModel(primaryModel);
            receiverCandidates.setModel(receiverModel);
            if (preferred != null) builderCandidates.setSelectedItem(preferred);
            if (receiverCandidates.getItemCount() > 0) receiverCandidates.setSelectedIndex(0);
            if (builderCandidates.getSelectedItem() instanceof EmailFieldChoice choice && !choice.isNone()) {
                canonical.setText(choice.candidate().email());
            }
            updateScenarioControls();
            tabs.setSelectedComponent(builderPanel);
            updateMatrixCount();
            setStatus("Loaded request into Test Builder with " + candidates.size() + " detected email occurrence(s)." +
                    (candidates.size() > 1 ? " Use the numbered selectors to choose the primary and optional receiver occurrence." : ""));
        });
    }

    private void candidateChanged() {
        Object o = builderCandidates.getSelectedItem();
        if (o instanceof EmailFieldChoice choice && !choice.isNone()) {
            canonical.setText(choice.candidate().email());
            EmailFieldChoice receiver = (EmailFieldChoice) receiverCandidates.getSelectedItem();
            if (receiver != null && !receiver.isNone() && sameCandidate(choice.candidate(), receiver.candidate())) {
                receiverCandidates.setSelectedIndex(0);
            }
        }
        updateScenarioControls();
    }

    private static boolean sameCandidate(EmailCandidate a, EmailCandidate b) {
        if (a == null || b == null) return false;
        if (a.hasExactRawTarget() && b.hasExactRawTarget()) return a.rawOffset() == b.rawOffset();
        return a.equals(b);
    }

    private void updateScenarioControls() {
        controlledReceiver.setEnabled(receiverControlled.isSelected());

        EmailFieldChoice primaryChoice = builderCandidates.getSelectedItem() instanceof EmailFieldChoice c ? c : null;
        EmailFieldChoice secondaryChoice = receiverCandidates.getSelectedItem() instanceof EmailFieldChoice c ? c : null;
        if (primaryChoice != null && secondaryChoice != null && !secondaryChoice.isNone() &&
                sameCandidate(primaryChoice.candidate(), secondaryChoice.candidate())) {
            receiverCandidates.setSelectedIndex(0);
            secondaryChoice = EmailFieldChoice.none();
        }

        String identity = primaryChoice == null || primaryChoice.isNone()
                ? "<select a primary email occurrence>"
                : primaryChoice.toString();
        String receiverSource = receiverControlled.isSelected()
                ? (controlledReceiver.getText().trim().isBlank() ? "the controlled mailbox you enter" : controlledReceiver.getText().trim())
                : "fresh unique Burp Collaborator addresses";

        JCheckBox dual = familyChecks.get("Two competing addresses in one field");
        boolean dualSelected = dual != null && dual.isSelected();
        boolean hasSecondary = secondaryChoice != null && !secondaryChoice.isNone();

        StringBuilder summary = new StringBuilder();
        summary.append("PRIMARY: ").append(identity).append(". ");
        summary.append("General parser families mutate only this occurrence. Receiver-directed cases use ")
                .append(receiverSource).append(". ");

        if (dualSelected) {
            summary.append("'Two competing addresses in one field' is selected, so those specific probes deliberately place BOTH the original identity email and the alternate/receiver address inside the PRIMARY field. ");
        } else {
            summary.append("'Two competing addresses in one field' is not selected, so no probe deliberately combines two complete addresses inside the primary field. ");
        }

        int customCount = includeCustomMutations.isSelected() ? CustomMutationParser.parse(customMutations.getText()).size() : 0;
        if (customCount > 0) {
            summary.append(customCount).append(" custom pasted mutation").append(customCount == 1 ? " is" : "s are")
                    .append(" also included in this run. ");
        }

        if (hasSecondary) {
            summary.append("SECOND REQUEST OCCURRENCE: ").append(secondaryChoice)
                    .append(" will also be replaced independently with the selected receiver source on each normal test. ");
            if (receiverCollaborator.isSelected()) {
                summary.append("Its Collaborator payload is separately correlated so Atomizer can distinguish primary-field activity from second-occurrence activity.");
            } else {
                summary.append("Both primary receiver-directed mutations and this second occurrence target the controlled mailbox.");
            }
        } else {
            summary.append("No second request occurrence will be changed.");
        }
        scenarioSummary.setText(summary.toString());
    }

    private void runBuilderMatrix() {
        HttpRequest request = builderRequest;
        if (request == null) {
            setStatus("Load a request into Test Builder first.");
            return;
        }
        Object selected = builderCandidates.getSelectedItem();
        if (!(selected instanceof EmailFieldChoice primaryChoice) || primaryChoice.isNone()) {
            setStatus("Choose a primary email occurrence first.");
            return;
        }
        EmailCandidate candidate = primaryChoice.candidate();

        EmailCandidate receiver = null;
        EmailFieldChoice receiverChoice = receiverCandidates.getSelectedItem() instanceof EmailFieldChoice c ? c : null;
        if (receiverChoice != null && !receiverChoice.isNone()) {
            if (sameCandidate(candidate, receiverChoice.candidate())) {
                setStatus("The additional receiver occurrence must be different from the primary occurrence.");
                return;
            }
            receiver = receiverChoice.candidate();
        }

        canonical.setText(candidate.email());
        List<MutationCase> selectedMutations = selectedMatrixMutations();
        if (selectedMutations.isEmpty()) {
            setStatus("Select at least one mutation matrix or paste at least one custom mutation.");
            return;
        }
        String receiverOverride = receiverControlled.isSelected() ? controlledReceiver.getText().trim() : "";
        if (receiverControlled.isSelected() && receiverOverride.isBlank()) {
            setStatus("Enter an email address you control, or choose Burp Collaborator.");
            return;
        }
        if (receiverControlled.isSelected() && (!receiverOverride.contains("@") || receiverOverride.startsWith("@") || receiverOverride.endsWith("@"))) {
            setStatus("Controlled receiver must look like an email address (local@domain).");
            return;
        }

        boolean useControlledReceiverForPrimary = receiverControlled.isSelected();
        appendActivity("RUN requested: " + request.method() + " " + request.url() +
                " | primary occurrence=#" + primaryChoice.index() + " " + candidate.email() +
                (receiverChoice == null || receiverChoice.isNone() ? "" : " | additional receiver occurrence=#" + receiverChoice.index() + " " + receiver.email()) +
                (receiverOverride.isBlank() ? " | receiver source=Collaborator" : " | receiver source=" + receiverOverride) +
                " | selected=" + selectedMutations.size());
        tabs.setSelectedIndex(3); // Results
        state.runMatrix(request, candidate, receiver, receiverOverride, useControlledReceiverForPrimary,
                selectedMutations, matrixRunConfig());
    }

    private void updateMutationDescription() {
        MutationCase m = selectedMutation();
        mutationDescription.setText(m == null ? "" : m.id() + " — " + m.intent());
    }

    private void selectConservativePreset() {
        for (Map.Entry<String, JCheckBox> e : familyChecks.entrySet()) {
            String f = e.getKey();
            e.getValue().setSelected(f.equals("Baseline") || f.equals("RFC parser probes") ||
                    f.equals("Legacy routing") || f.equals("Encoded-word"));
        }
        updateMatrixCount();
        updateScenarioControls();
    }


    private void selectBaselineOnlyPreset() {
        for (Map.Entry<String, JCheckBox> e : familyChecks.entrySet()) {
            e.getValue().setSelected(e.getKey().equals("Baseline"));
        }
        updateMatrixCount();
        updateScenarioControls();
    }

    private void selectAllFamilies() {
        familyChecks.values().forEach(b -> b.setSelected(true));
        updateMatrixCount();
        updateScenarioControls();
    }

    private void selectNoFamilies() {
        familyChecks.values().forEach(b -> b.setSelected(false));
        updateMatrixCount();
        updateScenarioControls();
    }

    private void selectOastFamilies() {
        for (Map.Entry<String, JCheckBox> e : familyChecks.entrySet()) {
            boolean any = state.mutations().stream().anyMatch(m -> m.family().equals(e.getKey()) && m.collaboratorCapable());
            e.getValue().setSelected(any);
        }
        updateMatrixCount();
        updateScenarioControls();
    }

    private void selectIdna2008Families() {
        for (Map.Entry<String, JCheckBox> e : familyChecks.entrySet()) {
            String family = e.getKey();
            e.getValue().setSelected(family.equals("Baseline") || family.startsWith("IDNA2008 "));
        }
        updateMatrixCount();
        updateScenarioControls();
    }

    private void selectDualAddressFamilies() {
        for (Map.Entry<String, JCheckBox> e : familyChecks.entrySet()) {
            e.getValue().setSelected(e.getKey().equals("Baseline") || e.getKey().equals("Two competing addresses in one field"));
        }
        updateMatrixCount();
        updateScenarioControls();
    }

    private void updateMatrixCount() {
        if (matrixCount == null) return;
        List<MutationCase> selected = selectedMatrixMutations();
        int count = selected.size();
        boolean baselineSelected = selected.stream().anyMatch(m -> m.id().equals("BASE-001"));
        int effective = baselineSelected ? count : count + (count > 0 ? 1 : 0);
        int cap = ((Number) maxTests.getValue()).intValue();
        if (cap > 0) effective = Math.min(effective, cap);

        int sentinels = 0;
        if (deliverySentinelsEnabled.isSelected() && effective > 0) {
            int nonBaseline = Math.max(0, effective - 1);
            int interval = ((Number) deliverySentinelEvery.getValue()).intValue();
            sentinels = 2; // start + final
            if (interval > 0 && nonBaseline > 1) {
                sentinels += (nonBaseline - 1) / interval;
            }
        }
        int total = effective + sentinels;
        String suffix = sentinels > 0 ? " (" + effective + " mutation/control + " + sentinels + " delivery sentinel)" : "";
        matrixCount.setText(total + " request" + (total == 1 ? "" : "s") + suffix);
    }

    public List<MutationCase> selectedMatrixMutations() {
        List<MutationCase> out = new ArrayList<>();
        for (MutationCase m : state.mutations()) {
            JCheckBox box = familyChecks.get(m.family());
            if (box == null || !box.isSelected()) continue;
            if (!includeProbeOnly.isSelected() && !m.collaboratorCapable() && !m.id().startsWith("BASE-")) continue;
            out.add(m);
        }
        if (includeCustomMutations.isSelected()) {
            out.addAll(CustomMutationParser.parse(customMutations.getText()));
        }
        return out;
    }

    public MatrixRunConfig matrixRunConfig() {
        return new MatrixRunConfig(
                ((Number) delay.getValue()).intValue(),
                ((Number) collaboratorWindow.getValue()).intValue(),
                ((Number) maxTests.getValue()).intValue(),
                stopStatusCodes.getText().trim(),
                stopResponseText.getText(),
                respectRetryAfter.isSelected(),
                ((Number) fallback429Delay.getValue()).intValue(),
                deliverySentinelsEnabled.isSelected(),
                ((Number) deliverySentinelEvery.getValue()).intValue());
    }

    public boolean liveEnabled() { return liveEnabled.isSelected(); }
    public boolean scopeOnly() { return scopeOnly.isSelected(); }
    public boolean skipGet() { return skipGet.isSelected(); }
    public boolean passiveDiscoveryEnabled() { return passiveEnabled.isSelected(); }
    public boolean passiveScopeOnly() { return passiveScopeOnly.isSelected(); }
    public boolean passiveSkipGet() { return passiveSkipGet.isSelected(); }
    public boolean includeProbeOnly() { return includeProbeOnly.isSelected(); }
    public boolean respectRetryAfterSelected() { return respectRetryAfter.isSelected(); }
    public String canonicalEmail() { return canonical.getText().trim(); }
    public String fallbackDomain() { return fallbackDomain.getText().trim(); }
    public int matrixDelayMs() { return ((Number) delay.getValue()).intValue(); }
    public MutationCase selectedMutation() { return (MutationCase) liveMutation.getSelectedItem(); }

    public void setCanonicalEmail(String email) {
        SwingUtilities.invokeLater(() -> canonical.setText(email == null ? "" : email.trim()));
    }

    public void setControlledReceiver(String email) {
        SwingUtilities.invokeLater(() -> {
            controlledReceiver.setText(email == null ? "" : email.trim());
            receiverControlled.setSelected(true);
            updateScenarioControls();
        });
    }

    public void refreshCollaboratorInbox() {
        if (collaboratorAccountsPanel != null) collaboratorAccountsPanel.refresh();
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> status.setText(text));
    }

    public void appendActivity(String text) {
        SwingUtilities.invokeLater(() -> {
            runLog.append("[" + java.time.LocalTime.now().withNano(0) + "] " + text + "\n");
            runLog.setCaretPosition(runLog.getDocument().getLength());
        });
    }

    public void refreshResults() {
        resultModel.fireTableDataChanged();
        if (resultTable.getSelectedRow() >= 0) showSelectedResultDetails();
    }

    public void refreshDiscoveries() {
        discoveryModel.refresh();
    }

    private void copySelectedResult() {
        int row = resultTable.getSelectedRow();
        if (row < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(row);
        List<AtomResult> rs = state.results();
        if (modelRow >= rs.size()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(rs.get(modelRow).mutatedEmail), null);
        setStatus("Copied mutated email.");
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("email-atomizer-results.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (FileWriter w = new FileWriter(chooser.getSelectedFile())) {
            w.write("time,correlation,mutation,family,wire_mode,method,url,logical_mutated_email,logical_email_utf8_hex,intended_request_body_hex,request_body_hex,wire_verification,target_location,target_parameter,target_raw_offset,receiver_override,http_status,response_length,differential,signal,oast_summary,interaction_count,interaction_sequence,smtp_recipient,smtp_message,smtp_body,collaborator_details,notes\n");
            for (AtomResult r : state.results()) {
                w.write(csv(r.created.toString()) + "," + csv(r.correlationId) + "," + csv(r.mutationId) + "," +
                        csv(r.family) + "," + csv(r.wireMode) + "," + csv(r.method) + "," + csv(r.url) + "," + csv(r.mutatedEmail) + "," +
                        csv(r.logicalEmailUtf8Hex) + "," + csv(r.intendedRequestBodyHex) + "," + csv(r.requestBodyHex) + "," +
                        csv(r.wireVerification) + "," + csv(r.targetLocation) + "," + csv(r.targetParameter) + "," +
                        csv(r.targetRawOffset) + "," + csv(r.secondaryReceiverEmail) + "," + csv(r.httpStatus) + "," + csv(r.responseLength) + "," +
                        csv(r.differential) + "," + csv(r.signal) + "," + csv(r.interactionSummary()) + "," +
                        csv(Integer.toString(r.interactionCount())) + "," + csv(r.interactionSequence()) + "," +
                        csv(r.smtpRecipient) + "," + csv(r.smtpMessage) + "," + csv(r.smtpBody) + "," +
                        csv(r.collaboratorTranscript()) + "," + csv(r.notes.get()) + "\n");
            }
            setStatus("Exported " + chooser.getSelectedFile().getName());
        } catch (IOException ex) {
            setStatus("CSV export failed: " + ex.getMessage());
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + sanitizeCsvText(s).replace("\"", "\"\"") + "\"";
    }

    /** CSV-safe text: preserve ordinary whitespace but escape embedded binary/control bytes such as NUL. */
    static String sanitizeCsvText(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch >= 0x20) {
                out.append(ch);
            } else {
                out.append(String.format("\\x%02X", (int) ch));
            }
        }
        return out.toString();
    }

    private static final class DiscoveryTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final transient AtomizerState state;
        private volatile List<EmailDiscovery> rows = List.of();
        private final String[] columns = {"Last seen", "Count", "#", "Host", "Method", "Operation", "Path", "Location", "Parameter", "Email", "Representation"};

        DiscoveryTableModel(AtomizerState state) {
            this.state = state;
            refresh();
        }

        void refresh() {
            rows = state.discoveries();
            fireTableDataChanged();
        }

        EmailDiscovery row(int index) {
            List<EmailDiscovery> snapshot = rows;
            return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            EmailDiscovery d = row(rowIndex);
            if (d == null) return "";
            return switch (columnIndex) {
                case 0 -> d.lastSeen.toString();
                case 1 -> d.seenCount.get();
                case 2 -> d.occurrenceIndex;
                case 3 -> d.host;
                case 4 -> d.method;
                case 5 -> d.operation;
                case 6 -> d.path;
                case 7 -> d.candidate.location();
                case 8 -> d.candidate.parameter();
                case 9 -> d.candidate.email();
                case 10 -> d.candidate.representation();
                default -> "";
            };
        }
    }

    private static final class ResultTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final transient AtomizerState state;
        private final String[] columns = {"Time", "ID", "Mutation", "Family", "Wire", "Method", "URL", "Mutated email", "Receiver override", "HTTP", "Len", "Δ baseline", "Signal", "OAST", "SMTP RCPT", "Notes"};

        ResultTableModel(AtomizerState state) { this.state = state; }
        @Override public int getRowCount() { return state.results().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            List<AtomResult> rows = state.results();
            if (rowIndex >= rows.size()) return "";
            AtomResult r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.created.toString();
                case 1 -> r.correlationId;
                case 2 -> r.mutationId + " " + r.label;
                case 3 -> r.family;
                case 4 -> r.wireMode;
                case 5 -> r.method;
                case 6 -> r.url;
                case 7 -> r.mutatedEmail;
                case 8 -> r.secondaryReceiverEmail;
                case 9 -> r.httpStatus;
                case 10 -> r.responseLength;
                case 11 -> r.differential;
                case 12 -> r.signal;
                case 13 -> r.interactionSummary();
                case 14 -> r.smtpRecipient;
                case 15 -> r.notes.get();
                default -> "";
            };
        }
    }
}
