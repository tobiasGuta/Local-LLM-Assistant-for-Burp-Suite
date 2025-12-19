package localai;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LocalLLMExtension implements BurpExtension {

    private MontoyaApi api;
    private CopilotTab copilotTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Bug Hunter's Copilot (Fixed Display)");

        // 1. Initialize the UI Tab
        this.copilotTab = new CopilotTab();
        api.userInterface().registerSuiteTab("Copilot", copilotTab);

        // 2. Register Context Menu
        api.userInterface().registerContextMenuItemsProvider(new ContextMenuProvider());

        api.logging().logToOutput("Bug Hunter's Copilot loaded. Unicode Rendering Fixed.");
    }

    // --- Context Menu (Right Click) ---
    class ContextMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            if (event.messageEditorRequestResponse().isPresent()) {
                JMenuItem analyzeItem = new JMenuItem("Send to Copilot");
                analyzeItem.addActionListener(e -> {
                    HttpRequestResponse reqResp = event.messageEditorRequestResponse().get().requestResponse();
                    copilotTab.loadContext(reqResp);
                });
                return List.of(analyzeItem);
            }
            return null;
        }
    }

    // --- Main UI Class ---
    class CopilotTab extends JPanel {
        private JEditorPane reportArea; // Renders the AI Analysis
        private JTextArea requestPreviewArea; // Shows the sanitized request
        private JTextField modelNameField;

        private HttpRequestResponse currentReqResp;
        private String fullContextString = ""; // Holds Request + Response

        // --- PROMPTS ---
        // 1. Analysis Prompt (The Microscope)
        private static final String PROMPT_ANALYZE =
                "SYSTEM ROLE: You are a Senior Security Analyst.\n" +
                        "GOAL: Analyze the HTTP Request AND Response to identify logical risks.\n" +
                        "FORMAT: Output strictly valid HTML. Follow this EXACT structure:\n\n" +
                        "<h3>Summary</h3>\n" +
                        "<p>[One sentence on what the endpoint did]</p>\n" +
                        "<h3>Data Anatomy</h3>\n" +
                        "<ul>\n" +
                        "  <li><b>User Inputs:</b> [List parameters sent]</li>\n" +
                        "  <li><b>Server Tokens:</b> [List tokens found]</li>\n" +
                        "</ul>\n" +
                        "<h3>Response Findings</h3>\n" +
                        "<ul>\n" +
                        "  <li><b>Status:</b> [e.g. 200 OK, 403 Forbidden]</li>\n" +
                        "  <li><b>Reflected Input:</b> [Did any user input appear in the response? YES/NO + Details]</li>\n" +
                        "  <li><b>Sensitive Data:</b> [Any PII, keys, or stack traces in response?]</li>\n" +
                        "</ul>\n" +
                        "<h3>Risk Hypotheses</h3>\n" +
                        "<ul>\n" +
                        "  <li><b>[Risk Name]:</b> [Why it might exist based on the evidence]</li>\n" +
                        "</ul>\n\n" +
                        "CONSTRAINTS:\n" +
                        " - DO NOT generate attack payloads (no <script>).\n" +
                        " - Keep descriptions concise and professional.";

        // 2. Report Draft Prompt (The Scribe)
        private static final String PROMPT_REPORT =
                "SYSTEM ROLE: You are a Professional Penetration Tester writing a report.\n" +
                        "GOAL: Draft a vulnerability finding based on this traffic.\n" +
                        "CONSTRAINTS: Output valid HTML using <h3>, <p>, and <pre> tags.\n" +
                        "TASK:\n" +
                        "Draft a generic finding template for this type of vulnerability.\n" +
                        "Include sections: **Title**, **Description**, **Impact**, **Remediation**.";

        public CopilotTab() {
            setLayout(new BorderLayout());

            // --- Top: Settings ---
            JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            settingsPanel.setBorder(new TitledBorder("Configuration"));
            settingsPanel.add(new JLabel("Local Model:"));
            modelNameField = new JTextField("dolphin-mistral", 15);
            settingsPanel.add(modelNameField);

            // --- Center: Split Pane (Request vs Analysis) ---
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setResizeWeight(0.4);

            // Left: Request Preview
            requestPreviewArea = new JTextArea();
            requestPreviewArea.setEditable(false);
            requestPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JScrollPane reqScroll = new JScrollPane(requestPreviewArea);
            reqScroll.setBorder(new TitledBorder("Sanitized Context (Req & Resp)"));

            // Right: AI Report (HTML)
            reportArea = new JEditorPane();
            reportArea.setContentType("text/html");
            reportArea.setEditable(false);
            reportArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            JScrollPane reportScroll = new JScrollPane(reportArea);
            reportScroll.setBorder(new TitledBorder("AI Analysis Report"));

            splitPane.setLeftComponent(reqScroll);
            splitPane.setRightComponent(reportScroll);

            // --- Bottom: Action Buttons ---
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

            JButton btnAnalyze = new JButton("Analyze Full Context");
            btnAnalyze.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnAnalyze.setBackground(new Color(60, 120, 216));
            btnAnalyze.setForeground(Color.BLACK);
            btnAnalyze.addActionListener(e -> runAI(PROMPT_ANALYZE));

            JButton btnReport = new JButton("Draft Finding Report");
            btnReport.addActionListener(e -> runAI(PROMPT_REPORT));

            JButton btnClear = new JButton("Clear");
            btnClear.addActionListener(e -> {
                reportArea.setText("");
                requestPreviewArea.setText("");
                currentReqResp = null;
            });

            actionPanel.add(btnAnalyze);
            actionPanel.add(btnReport);
            actionPanel.add(btnClear);

            // Assembly
            add(settingsPanel, BorderLayout.NORTH);
            add(splitPane, BorderLayout.CENTER);
            add(actionPanel, BorderLayout.SOUTH);

            setInitialWelcome();
        }

        private void setInitialWelcome() {
            reportArea.setText("<html><body style='font-family:sans-serif; padding:10px;'>" +
                    "<h2>Bug Hunter's Copilot</h2>" +
                    "<p>Ready to assist. Workflow:</p>" +
                    "<ul>" +
                    "<li>Right-click a request in Burp -> <b>Send to Copilot</b></li>" +
                    "<li>Review the sanitized data on the left.</li>" +
                    "<li>Click <b>Analyze</b> to get a logic breakdown.</li>" +
                    "</ul></body></html>");
        }

        // --- Logic: Load & Sanitize (Request AND Response) ---
        public void loadContext(HttpRequestResponse reqResp) {
            this.currentReqResp = reqResp;

            // 1. Process Request
            String rawReq = reqResp.request().toString();
            String cleanReq = sanitize(rawReq);

            // 2. Process Response (if it exists)
            String cleanResp = "[No Response Captured]";
            if (reqResp.hasResponse()) {
                String rawResp = reqResp.response().toString();

                // USER PREFERENCE: 90,000 char limit
                if (rawResp.length() > 90000) {
                    rawResp = rawResp.substring(0, 90000) + "\n...[TRUNCATED BY EXTENSION]...";
                }
                cleanResp = sanitize(rawResp);
            }

            // 3. Combine
            this.fullContextString = "=== HTTP REQUEST ===\n" + cleanReq + "\n\n=== HTTP RESPONSE ===\n" + cleanResp;

            // Update UI
            SwingUtilities.invokeLater(() -> {
                requestPreviewArea.setText(fullContextString);
                reportArea.setText("<html><body><h3>Context Loaded.</h3><p>Ready to analyze Request & Response.</p></body></html>");
            });
        }

        // --- The Sanitizer ---
        private String sanitize(String raw) {
            String clean = raw;
            // Redact common sensitive headers
            clean = clean.replaceAll("(?i)(Authorization:\\s+)(Bearer\\s+)?([\\w\\-\\._]+)", "$1$2[REDACTED_TOKEN]");
            clean = clean.replaceAll("(?i)(Cookie:\\s+)(.*)", "$1[REDACTED_COOKIES]");
            clean = clean.replaceAll("(?i)(X-API-Key:\\s+)(.*)", "$1[REDACTED_KEY]");
            return clean;
        }

        // --- Logic: Call AI ---
        private void runAI(String systemPrompt) {
            if (fullContextString.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No request loaded. Please send a request to Copilot first.");
                return;
            }

            reportArea.setText("<html><body><h3>Thinking...</h3><p>Reading Request & Response...</p></body></html>");

            CompletableFuture.runAsync(() -> {
                try {
                    String fullPrompt = systemPrompt + "\n\nFULL TRAFFIC CONTEXT:\n" + fullContextString;
                    String jsonSafePrompt = escapeJson(fullPrompt);

                    String model = modelNameField.getText().trim();
                    if(model.isEmpty()) model = "dolphin-mistral";

                    String jsonBody = "{\"model\": \"" + model + "\", \"prompt\": \"" + jsonSafePrompt + "\", \"stream\": false}";

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:11434/api/generate"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String rawAiResponse = extractResponseText(response.body());
                        SwingUtilities.invokeLater(() -> {
                            reportArea.setText("<html><body style='font-family:sans-serif; padding:10px;'>" + rawAiResponse + "</body></html>");
                            reportArea.setCaretPosition(0);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> reportArea.setText("Error: " + response.statusCode()));
                    }

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> reportArea.setText("Connection Error: " + e.getMessage()));
                }
            });
        }

        private String escapeJson(String data) {
            if (data == null) return "";
            return data.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

        private String extractResponseText(String json) {
            try {
                int startIndex = json.indexOf("\"response\":\"") + 12;
                int endIndex = json.lastIndexOf("\",\"done\"");
                if (startIndex > 12 && endIndex > startIndex) {
                    String content = json.substring(startIndex, endIndex);
                    // --- THE FIX: Handle Unicode Escapes like \u003c ---
                    return content
                            .replace("\\n", "\n")
                            .replace("\\r", "")
                            .replace("\\\"", "\"")
                            .replace("\\t", "\t")
                            .replace("\\\\", "\\")
                            .replace("\\u003c", "<")  // Decodes <
                            .replace("\\u003e", ">"); // Decodes >
                }
                return json;
            } catch (Exception e) { return json; }
        }
    }
}