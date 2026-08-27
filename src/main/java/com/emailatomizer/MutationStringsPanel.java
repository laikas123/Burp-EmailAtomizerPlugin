package com.emailatomizer;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Manual mutation generator for cases where replaying a whole matrix is not desirable.
 * This panel never sends HTTP requests; it only renders MutationCatalog values for copy/paste.
 */
public final class MutationStringsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient AtomizerState state;
    private final transient Supplier<String> selectedCanonicalSupplier;
    private final transient Supplier<String> fallbackDomainSupplier;

    private final JTextField identityEmail = new JTextField(34);
    private final JRadioButton receiverCollaborator = new JRadioButton("Fresh Burp Collaborator addresses (recommended)", true);
    private final JRadioButton receiverControlled = new JRadioButton("Email address I control", false);
    private final JTextField controlledReceiver = new JTextField(30);
    private final JComboBox<EncodingMode> encodingMode = new JComboBox<>(EncodingMode.values());
    private final Map<String, JCheckBox> familyChecks = new LinkedHashMap<>();
    private final MutationStringTableModel model = new MutationStringTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea description = new JTextArea(4, 80);
    private final JLabel status = new JLabel("Enter an email and click Generate mutation strings. No HTTP requests are sent.");

    public MutationStringsPanel(AtomizerState state,
                                Supplier<String> selectedCanonicalSupplier,
                                Supplier<String> fallbackDomainSupplier) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.selectedCanonicalSupplier = selectedCanonicalSupplier;
        this.fallbackDomainSupplier = fallbackDomainSupplier;

        controlledReceiver.setEnabled(false);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setText("Manual payload generator. Each selected mutation is rendered without sending anything. " +
                "Receiver-directed cases use a fresh Collaborator address by default, or a real mailbox you control. " +
                "Wire-mode mutations are shown in their actual copy/paste representation (for example JSON Unicode cases use \\uXXXX). " +
                "Changing the output encoding does not regenerate Collaborator addresses; click Generate again when you want fresh ones.");

        ButtonGroup receiverGroup = new ButtonGroup();
        receiverGroup.add(receiverCollaborator);
        receiverGroup.add(receiverControlled);
        receiverCollaborator.addActionListener(e -> updateReceiverControls());
        receiverControlled.addActionListener(e -> updateReceiverControls());
        encodingMode.addActionListener(e -> model.fireTableDataChanged());

        add(buildControls(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(155);
        table.getColumnModel().getColumn(4).setPreferredWidth(95);
        table.getColumnModel().getColumn(5).setPreferredWidth(640);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) copySelectedStrings();
            }
            @Override public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
        });
    }

    private JPanel buildControls() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBorder(BorderFactory.createTitledBorder("Mutation Strings — manual copy/paste generator"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        JButton useSelected = new JButton("Use selected Email Atomizer email");
        useSelected.addActionListener(e -> {
            String value = selectedCanonicalSupplier == null ? "" : selectedCanonicalSupplier.get();
            if (value != null) identityEmail.setText(value);
        });
        JPanel identity = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        identity.add(identityEmail);
        identity.add(useSelected);
        addRow(outer, c, 0, "Identity / starting email", identity);

        JPanel receiver = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        receiver.add(receiverCollaborator);
        receiver.add(receiverControlled);
        receiver.add(controlledReceiver);
        addRow(outer, c, 1, "Alternate / receiver used by receiver-directed mutations", receiver);

        JPanel format = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        format.add(encodingMode);
        JLabel hint = new JLabel("(format changes are local; they do not generate new payloads)");
        format.add(hint);
        addRow(outer, c, 2, "Copy/paste format", format);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        c.weightx = 1;
        outer.add(description, c);
        return outer;
    }

    private Component buildCenter() {
        JPanel families = new JPanel();
        families.setLayout(new BoxLayout(families, BoxLayout.Y_AXIS));
        families.setBorder(BorderFactory.createTitledBorder("Mutation families"));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MutationCase mutation : state.mutations()) {
            counts.merge(mutation.family(), 1, Integer::sum);
        }
        for (String family : state.mutationFamilies()) {
            JCheckBox box = new JCheckBox(family + " (" + counts.getOrDefault(family, 0) + ")", true);
            box.setActionCommand(family);
            familyChecks.put(family, box);
            families.add(box);
        }

        JPanel familyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton all = new JButton("All");
        JButton none = new JButton("None");
        JButton receiverDirected = new JButton("Receiver-directed only");
        all.addActionListener(e -> familyChecks.values().forEach(b -> b.setSelected(true)));
        none.addActionListener(e -> familyChecks.values().forEach(b -> b.setSelected(false)));
        receiverDirected.addActionListener(e -> {
            for (Map.Entry<String, JCheckBox> entry : familyChecks.entrySet()) {
                boolean capable = state.mutations().stream()
                        .anyMatch(m -> m.family().equals(entry.getKey()) && m.collaboratorCapable());
                entry.getValue().setSelected(capable);
            }
        });
        familyButtons.add(all);
        familyButtons.add(none);
        familyButtons.add(receiverDirected);
        families.add(Box.createVerticalStrut(4));
        families.add(familyButtons);

        JScrollPane familyScroll = new JScrollPane(families,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        familyScroll.setPreferredSize(new Dimension(320, 520));
        familyScroll.getVerticalScrollBar().setUnitIncrement(18);

        JScrollPane tableScroll = new JScrollPane(table);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, familyScroll, tableScroll);
        split.setResizeWeight(0.23);
        split.setDividerLocation(330);
        split.setOneTouchExpandable(true);
        return split;
    }

    private JPanel buildBottom() {
        JPanel outer = new JPanel(new BorderLayout(6, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton generate = new JButton("Generate mutation strings");
        JButton copySelected = new JButton("Copy selected string(s)");
        JButton copyAll = new JButton("Copy all strings");
        JButton copyLabeled = new JButton("Copy all with IDs / families");
        JButton clear = new JButton("Clear");

        generate.addActionListener(e -> generate());
        copySelected.addActionListener(e -> copySelectedStrings());
        copyAll.addActionListener(e -> copyAllStrings(false));
        copyLabeled.addActionListener(e -> copyAllStrings(true));
        clear.addActionListener(e -> {
            model.setRows(List.of());
            status.setText("Cleared. No HTTP requests are sent by this tab.");
        });

        buttons.add(generate);
        buttons.add(copySelected);
        buttons.add(copyAll);
        buttons.add(copyLabeled);
        buttons.add(clear);
        outer.add(buttons, BorderLayout.NORTH);
        outer.add(status, BorderLayout.SOUTH);
        return outer;
    }

    private void generate() {
        String identity = identityEmail.getText() == null ? "" : identityEmail.getText().trim();
        if (!validEmailShape(identity)) {
            JOptionPane.showMessageDialog(this,
                    "Enter a starting email containing a local part and domain.",
                    "Mutation Strings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean controlled = receiverControlled.isSelected();
        String receiver = controlledReceiver.getText() == null ? "" : controlledReceiver.getText().trim();
        if (controlled && !validEmailShape(receiver)) {
            JOptionPane.showMessageDialog(this,
                    "Enter the alternate/receiver email address you control, or choose Burp Collaborator.",
                    "Mutation Strings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<MutationStringRow> rows = new ArrayList<>();
        int failures = 0;
        String fallback = fallbackDomainSupplier == null ? "collaborator.invalid" : fallbackDomainSupplier.get();
        for (MutationCase mutation : state.mutations()) {
            JCheckBox family = familyChecks.get(mutation.family());
            if (family == null || !family.isSelected()) continue;
            try {
                AtomizerState.RenderedMutation rendered = state.renderForMutationStrings(
                        mutation, identity, controlled, receiver, fallback);
                rows.add(new MutationStringRow(mutation, rendered.email(), rendered.receiverEmail(), rendered.correlationId()));
            } catch (Throwable t) {
                failures++;
            }
        }
        model.setRows(rows);
        if (!rows.isEmpty()) table.setRowSelectionInterval(0, 0);
        String receiverText = controlled ? "controlled receiver " + receiver : "fresh Collaborator addresses";
        status.setText("Generated " + rows.size() + " mutation strings using " + receiverText +
                (failures == 0 ? "." : "; " + failures + " failed to render.") +
                " Nothing was sent to the target.");
    }

    private void updateReceiverControls() {
        controlledReceiver.setEnabled(receiverControlled.isSelected());
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row, row);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy selected in current format");
        JMenuItem logical = new JMenuItem("Copy selected logical Unicode mutation");
        copy.addActionListener(ev -> copySelectedStrings());
        logical.addActionListener(ev -> copySelectedLogical());
        menu.add(copy);
        menu.add(logical);
        menu.show(table, e.getX(), e.getY());
    }

    private void copySelectedStrings() {
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) return;
        List<String> values = new ArrayList<>();
        for (int viewRow : selected) {
            int row = table.convertRowIndexToModel(viewRow);
            values.add(encode(model.row(row)));
        }
        copyToClipboard(String.join(System.lineSeparator(), values));
        status.setText("Copied " + values.size() + " mutation string" + (values.size() == 1 ? "" : "s") +
                " as " + encodingMode.getSelectedItem() + ".");
    }

    private void copySelectedLogical() {
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) return;
        List<String> values = new ArrayList<>();
        for (int viewRow : selected) {
            int row = table.convertRowIndexToModel(viewRow);
            values.add(model.row(row).logicalMutation());
        }
        copyToClipboard(String.join(System.lineSeparator(), values));
        status.setText("Copied " + values.size() + " logical Unicode mutation string" + (values.size() == 1 ? "" : "s") + ".");
    }

    private void copyAllStrings(boolean labeled) {
        if (model.getRowCount() == 0) return;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            MutationStringRow row = model.row(i);
            String value = encode(row);
            if (labeled) {
                lines.add(row.mutation().id() + "\t" + row.mutation().family() + "\t" + row.mutation().label() + "\t" + value);
            } else {
                lines.add(value);
            }
        }
        copyToClipboard(String.join(System.lineSeparator(), lines));
        status.setText("Copied all " + model.getRowCount() + (labeled ? " labeled" : "") +
                " mutation strings as " + encodingMode.getSelectedItem() + ".");
    }

    private String encode(MutationStringRow row) {
        EncodingMode mode = (EncodingMode) encodingMode.getSelectedItem();
        if (mode == null) mode = EncodingMode.RAW;

        String wire = manualWireValue(row.mutation(), row.logicalMutation());
        boolean alreadyJsonWire = isJsonValueWireMode(row.mutation().wireMode());

        return switch (mode) {
            case RAW -> wire;
            // JSON wire modes already contain the exact JSON string-content representation that
            // should be pasted between quotes. Re-escaping their backslashes would silently turn
            // \\u00e4 into \\\\u00e4 and test a different parser path.
            case JSON_ESCAPED -> alreadyJsonWire ? wire : RequestMutator.jsonEscape(wire);
            case JSON_STRING_LITERAL -> alreadyJsonWire
                    ? "\"" + wire + "\""
                    : "\"" + RequestMutator.jsonEscape(wire) + "\"";
            case URL_FORM -> RequestMutator.urlEncode(wire);
            case URL_PERCENT -> RequestMutator.urlEncode(wire).replace("+", "%20");
            case DOUBLE_URL -> RequestMutator.doubleUrlEncode(wire);
        };
    }

    /**
     * Render the exact single-value representation a wire-mode mutation is intended to place on
     * the HTTP wire. The logical mutation remains available separately for inspection/copying.
     */
    static String manualWireValue(MutationCase mutation, String logical) {
        if (mutation == null) return logical == null ? "" : logical;
        if (logical == null) logical = "";
        return switch (mutation.wireMode()) {
            case JSON_UNICODE_ESCAPED -> RequestMutator.jsonEscapeUnicodeWire(logical, false, false, false);
            case JSON_DOUBLE_UNICODE_ESCAPED -> RequestMutator.doubleJsonUnicodeEscapes(logical);
            case JSON_ESCAPE_AT -> RequestMutator.jsonEscapeUnicodeWire(logical, true, false, false);
            case JSON_ESCAPE_DOT -> RequestMutator.jsonEscapeUnicodeWire(logical, false, true, false);
            case JSON_ESCAPE_PLUS -> RequestMutator.jsonEscapeUnicodeWire(logical, false, false, true);
            // Duplicate-key modes are structural request mutations, not a standalone email value.
            // Mutation Strings therefore keeps their logical alternate value and exposes the mode
            // in the table instead of pretending a complete JSON object can be represented here.
            default -> logical;
        };
    }

    private static boolean isJsonValueWireMode(MutationCase.WireMode mode) {
        return mode == MutationCase.WireMode.JSON_UNICODE_ESCAPED ||
                mode == MutationCase.WireMode.JSON_DOUBLE_UNICODE_ESCAPED ||
                mode == MutationCase.WireMode.JSON_ESCAPE_AT ||
                mode == MutationCase.WireMode.JSON_ESCAPE_DOT ||
                mode == MutationCase.WireMode.JSON_ESCAPE_PLUS;
    }

    private static boolean validEmailShape(String value) {
        if (value == null) return false;
        int at = value.lastIndexOf('@');
        return at > 0 && at < value.length() - 1;
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component value) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(value, c);
    }

    private enum EncodingMode {
        RAW("Raw"),
        JSON_ESCAPED("JSON escaped value"),
        JSON_STRING_LITERAL("JSON string literal (includes quotes)"),
        URL_FORM("Form / query encoded (spaces +)"),
        URL_PERCENT("URL percent encoded (spaces %20)"),
        DOUBLE_URL("Double URL encoded");

        private final String label;
        EncodingMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private record MutationStringRow(MutationCase mutation, String logicalMutation, String receiverEmail, String correlationId) {}

    private final class MutationStringTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Family", "Label", "Wire mode", "Receiver-directed", "Mutation string"};
        private List<MutationStringRow> rows = new ArrayList<>();

        void setRows(List<MutationStringRow> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        MutationStringRow row(int index) { return rows.get(index); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            MutationStringRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.mutation().id();
                case 1 -> row.mutation().family();
                case 2 -> row.mutation().label();
                case 3 -> row.mutation().wireMode().name();
                case 4 -> row.mutation().collaboratorCapable() ? "yes" : "no";
                case 5 -> encode(row);
                default -> "";
            };
        }
    }
}
