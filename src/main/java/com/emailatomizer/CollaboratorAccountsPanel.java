package com.emailatomizer;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generates stable Collaborator-backed addresses for creating test accounts before a matrix is run.
 * Generating/copying addresses never sends a request to the target application.
 */
public final class CollaboratorAccountsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient AtomizerState state;
    private final transient Consumer<String> canonicalConsumer;
    private final transient Consumer<String> controlledReceiverConsumer;

    private final JTextField localA = new JTextField("laikas", 14);
    private final JTextField labelA = new JTextField("gmail", 12);
    private final JTextField addressA = readonlyField(60);
    private final JTextField localB = new JTextField("laikas", 14);
    private final JTextField labelB = new JTextField("yahoo", 12);
    private final JTextField addressB = readonlyField(60);

    private final JLabel connectionStatus = new JLabel("Checking Burp Collaborator...");
    private final JLabel accountStatus = new JLabel("Generate A/B, create the target accounts at your own pace, then test later. No target traffic is sent here.");
    private final JButton reconnectButton = new JButton("Retry Collaborator connection");
    private final JButton generateAButton = new JButton("Generate / regenerate A");
    private final JButton generateBButton = new JButton("Generate / regenerate B");
    private final JButton generateBothButton = new JButton("Generate both A + B");

    private final InboxTableModel inboxModel;
    private final JTable inboxTable;
    private final JTextArea inboxDetail = new JTextArea();
    private final JLabel inboxStatus = new JLabel("Persistent-account events stay here and are also copied into the active Results row for normal Results CSV export.");
    private final JButton pollButton = new JButton("Poll now");

    public CollaboratorAccountsPanel(AtomizerState state,
                                     Consumer<String> canonicalConsumer,
                                     Consumer<String> controlledReceiverConsumer) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.canonicalConsumer = canonicalConsumer;
        this.controlledReceiverConsumer = controlledReceiverConsumer;
        this.inboxModel = new InboxTableModel(state);
        this.inboxTable = new JTable(inboxModel);

        inboxDetail.setEditable(false);
        inboxDetail.setLineWrap(false);
        inboxDetail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        reconnectButton.addActionListener(e -> retryCollaboratorConnection());
        generateAButton.addActionListener(e -> generate("A", localA, labelA));
        generateBButton.addActionListener(e -> generate("B", localB, labelB));
        generateBothButton.addActionListener(e -> generateBoth());

        Component accounts = buildAccounts();
        JScrollPane accountScroll = new JScrollPane(accounts,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        accountScroll.setBorder(null);
        accountScroll.getVerticalScrollBar().setUnitIncrement(18);

        // Use dedicated inner tabs rather than stacking the inbox beneath the account generator. Burp can give
        // extension tabs surprisingly little vertical space; a nested tab guarantees that the inbox table/details
        // always receive the full available viewport instead of being clipped below the fold.
        JTabbedPane sections = new JTabbedPane();
        sections.addTab("Persistent accounts", accountScroll);
        sections.addTab("Collaborator inbox", buildInbox());
        add(sections, BorderLayout.CENTER);
        refresh();
    }

    private JPanel buildAccounts() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBorder(BorderFactory.createTitledBorder("Persistent Collaborator test accounts"));

        JTextArea help = new JTextArea(
                "1) Generate A and B here. 2) Copy those exact addresses into the target application and create the accounts. " +
                "3) Leave Atomizer loaded while you work; there is no countdown. 4) Later, run mutations; persistent-account SMTP/DNS/HTTP is copied into the matching Results row and also retained in the Collaborator inbox tab. " +
                "Generating an address only asks Burp Collaborator for a payload; it does NOT send a request to the target. " +
                "Regenerating a slot makes a new address, while late events for older generated addresses remain correlatable for this loaded session.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setBorder(BorderFactory.createEmptyBorder(2, 5, 5, 5));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.add(help);

        JPanel connection = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        connection.setAlignmentX(Component.LEFT_ALIGNMENT);
        connection.add(new JLabel("Collaborator:"));
        connection.add(connectionStatus);
        connection.add(reconnectButton);
        connection.add(generateBothButton);
        outer.add(connection);

        outer.add(buildAccountCard("A", localA, labelA, addressA, generateAButton));
        outer.add(buildAccountCard("B", localB, labelB, addressB, generateBButton));

        accountStatus.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        accountStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.add(accountStatus);
        return outer;
    }

    private JPanel buildAccountCard(String slot, JTextField local, JTextField label,
                                    JTextField address, JButton generateButton) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder("Account " + slot));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        card.add(new JLabel("Local part"), c);
        c.gridx = 1;
        c.weightx = 0.25;
        card.add(local, c);
        c.gridx = 2;
        c.weightx = 0;
        card.add(new JLabel("Domain label"), c);
        c.gridx = 3;
        c.weightx = 0.25;
        card.add(label, c);
        c.gridx = 4;
        c.weightx = 0;
        card.add(generateButton, c);

        JButton copy = new JButton("Copy address");
        copy.addActionListener(e -> copy(address.getText()));

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        card.add(new JLabel("Generated"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.weightx = 1;
        card.add(address, c);
        c.gridx = 4;
        c.gridwidth = 1;
        c.weightx = 0;
        card.add(copy, c);

        JButton canonical = new JButton("Use as canonical");
        JButton receiver = new JButton("Use as controlled receiver");
        canonical.addActionListener(e -> {
            if (!address.getText().isBlank() && canonicalConsumer != null) canonicalConsumer.accept(address.getText());
        });
        receiver.addActionListener(e -> {
            if (!address.getText().isBlank() && controlledReceiverConsumer != null) controlledReceiverConsumer.accept(address.getText());
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actions.add(canonical);
        actions.add(receiver);
        c.gridy = 2;
        c.gridx = 1;
        c.gridwidth = 4;
        c.weightx = 1;
        card.add(actions, c);

        return card;
    }

    private Component buildInbox() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Atomizer Collaborator inbox"));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clear = new JButton("Clear displayed inbox");
        JButton export = new JButton("Export inbox CSV");
        pollButton.addActionListener(e -> {
            if (!state.collaboratorAvailable()) {
                inboxStatus.setText("Collaborator is not connected. Use Retry Collaborator connection above.");
                return;
            }
            state.pollCollaboratorNow();
            refresh();
            inboxStatus.setText("Polled Atomizer's Collaborator client.");
        });
        clear.addActionListener(e -> {
            state.clearCollaboratorInbox();
            inboxDetail.setText("");
            inboxStatus.setText("Displayed inbox cleared; generated account correlations remain active.");
        });
        export.addActionListener(e -> exportInboxCsv());
        buttons.add(pollButton);
        buttons.add(clear);
        buttons.add(export);
        panel.add(buttons, BorderLayout.NORTH);

        inboxTable.setAutoCreateRowSorter(true);
        inboxTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inboxTable.setFillsViewportHeight(true);
        inboxTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        inboxTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        inboxTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        inboxTable.getColumnModel().getColumn(3).setPreferredWidth(310);
        inboxTable.getColumnModel().getColumn(4).setPreferredWidth(430);
        inboxTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });

        JScrollPane tableScroll = new JScrollPane(inboxTable);
        JScrollPane detailScroll = new JScrollPane(inboxDetail);
        tableScroll.setMinimumSize(new Dimension(260, 120));
        detailScroll.setMinimumSize(new Dimension(220, 120));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailScroll);
        split.setResizeWeight(0.62);
        split.setDividerLocation(0.62);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);

        JPanel center = new JPanel(new BorderLayout());
        center.add(split, BorderLayout.CENTER);
        center.add(inboxStatus, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void retryCollaboratorConnection() {
        if (state.collaboratorAvailable()) {
            accountStatus.setText("Collaborator is already connected. Existing generated account roots were kept unchanged.");
            refresh();
            return;
        }
        accountStatus.setText("Retrying Burp Collaborator connection...");
        try {
            state.initializeCollaborator();
            if (state.collaboratorAvailable()) {
                accountStatus.setText("Collaborator connected. You can generate A/B now.");
            } else {
                accountStatus.setText("Collaborator is still unavailable. Check Email Atomizer extension output for the error.");
            }
        } catch (Throwable t) {
            accountStatus.setText("Collaborator retry failed: " + safeMessage(t));
        }
        refresh();
    }

    private void generateBoth() {
        if (!state.collaboratorAvailable()) {
            accountStatus.setText("Collaborator is not connected. Click Retry Collaborator connection first.");
            refresh();
            return;
        }
        try {
            PersistentCollaboratorAccount a = state.generatePersistentCollaboratorAccount("A", localA.getText(), labelA.getText());
            addressA.setText(a.email());
            PersistentCollaboratorAccount b = state.generatePersistentCollaboratorAccount("B", localB.getText(), labelB.getText());
            addressB.setText(b.email());
            accountStatus.setText("Generated both accounts with separate Collaborator roots. Copy A/B into the target and take as long as needed before testing.");
        } catch (Throwable t) {
            accountStatus.setText("Could not generate both accounts: " + safeMessage(t));
            JOptionPane.showMessageDialog(this, safeMessage(t), "Collaborator account generation", JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private void generate(String slot, JTextField local, JTextField label) {
        if (!state.collaboratorAvailable()) {
            accountStatus.setText("Collaborator is not connected. Click Retry Collaborator connection first.");
            refresh();
            return;
        }
        try {
            PersistentCollaboratorAccount account = state.generatePersistentCollaboratorAccount(slot, local.getText(), label.getText());
            if ("B".equalsIgnoreCase(slot)) addressB.setText(account.email());
            else addressA.setText(account.email());
            accountStatus.setText("Generated account " + slot + ": " + account.email() +
                    " — monitoring is active; take as long as needed to create the target account before testing.");
        } catch (Throwable t) {
            accountStatus.setText("Could not generate account " + slot + ": " + safeMessage(t));
            JOptionPane.showMessageDialog(this, safeMessage(t), "Collaborator account generation", JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private void exportInboxCsv() {
        List<CollaboratorInboxEvent> rows = state.collaboratorInbox();
        if (rows.isEmpty()) {
            inboxStatus.setText("Inbox is empty; nothing to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("email-atomizer-collaborator-inbox.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try (FileWriter w = new FileWriter(file)) {
            w.write("time,correlation,source,type,smtp_rcpt_to,generated_address,collaborator_root,label,details\n");
            for (CollaboratorInboxEvent e : rows) {
                w.write(csv(e.timestamp() == null ? "" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(e.timestamp())) + "," +
                        csv(e.correlationId()) + "," + csv(e.source()) + "," + csv(e.type()) + "," +
                        csv(e.smtpRecipient()) + "," + csv(e.generatedAddress()) + "," +
                        csv(e.collaboratorRoot()) + "," + csv(e.label()) + "," + csv(e.details()) + "\n");
            }
            inboxStatus.setText("Exported " + rows.size() + " inbox event" + (rows.size() == 1 ? "" : "s") + " to " + file.getName() + ".");
        } catch (IOException ex) {
            inboxStatus.setText("Inbox CSV export failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Collaborator inbox export", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String value) {
        if (value == null) return "\"\"";
        return "\"" + AtomizerPanel.sanitizeCsvText(value).replace("\"", "\"\"") + "\"";
    }

    private void showSelected() {
        int row = inboxTable.getSelectedRow();
        if (row < 0) return;
        int modelRow = inboxTable.convertRowIndexToModel(row);
        CollaboratorInboxEvent event = inboxModel.row(modelRow);
        inboxDetail.setText(event == null ? "" : event.details());
        inboxDetail.setCaretPosition(0);
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            PersistentCollaboratorAccount a = state.persistentCollaboratorAccount("A");
            PersistentCollaboratorAccount b = state.persistentCollaboratorAccount("B");
            if (a != null) addressA.setText(a.email());
            if (b != null) addressB.setText(b.email());
            inboxModel.refresh();

            boolean connected = state.collaboratorAvailable();
            if (connected) {
                connectionStatus.setText("CONNECTED — ready to generate/poll");
                connectionStatus.setToolTipText("Atomizer has its own active Burp Collaborator client.");
            } else {
                connectionStatus.setText("NOT CONNECTED");
                connectionStatus.setToolTipText("Click Retry Collaborator connection.");
            }
            reconnectButton.setEnabled(!connected);
            generateAButton.setEnabled(connected);
            generateBButton.setEnabled(connected);
            generateBothButton.setEnabled(connected);
            pollButton.setEnabled(connected);

            if (connected && accountStatus.getText().startsWith("Burp Collaborator is unavailable")) {
                accountStatus.setText("Collaborator connected. Generate A/B when ready; no target request is sent by generation.");
            } else if (!connected && !accountStatus.getText().startsWith("Collaborator retry failed")) {
                accountStatus.setText("Burp Collaborator is not connected. Click Retry Collaborator connection.");
            }

            if (inboxTable.getSelectedRow() >= 0) showSelected();
        });
    }

    private static JTextField readonlyField(int columns) {
        JTextField field = new JTextField(columns);
        field.setEditable(false);
        return field;
    }

    private static void copy(String value) {
        if (value == null || value.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private static final class InboxTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final transient AtomizerState state;
        private volatile List<CollaboratorInboxEvent> rows = List.of();
        private final String[] columns = {"Time", "Source", "Type", "SMTP RCPT TO", "Generated address / mutation"};

        InboxTableModel(AtomizerState state) {
            this.state = state;
            refresh();
        }

        void refresh() {
            rows = state.collaboratorInbox();
            fireTableDataChanged();
        }

        CollaboratorInboxEvent row(int index) {
            if (index < 0 || index >= rows.size()) return null;
            return rows.get(index);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            CollaboratorInboxEvent e = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.timestamp() == null ? "" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(e.timestamp());
                case 1 -> e.source();
                case 2 -> e.type();
                case 3 -> e.smtpRecipient();
                case 4 -> e.generatedAddress().isBlank() ? e.label() : e.generatedAddress();
                default -> "";
            };
        }
    }
}
