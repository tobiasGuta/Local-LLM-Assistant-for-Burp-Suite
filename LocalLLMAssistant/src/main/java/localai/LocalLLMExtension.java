package localai;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LocalLLMExtension implements BurpExtension {

    private MontoyaApi api;
    private AITab aiTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Local LLM Assistant (Red Team)");

        // 1. Initialize the UI Tab
        this.aiTab = new AITab();
        api.userInterface().registerSuiteTab("Local AI", aiTab);

        // 2. Register Context Menu
        api.userInterface().registerContextMenuItemsProvider(new ContextMenuProvider());

        api.logging().logToOutput("Local AI Tab loaded. Markdown Rendering Fixed.");
    }

    // --- The Right-Click Menu ---
    class ContextMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            if (event.messageEditorRequestResponse().isPresent()) {
                JMenuItem askAiItem = new JMenuItem("Ask Local AI...");
                askAiItem.addActionListener(e -> {
                    HttpRequestResponse reqResp = event.messageEditorRequestResponse().get().requestResponse();
                    String userPrompt = JOptionPane.showInputDialog(null,
                            "What should the AI check?", "Ask Dolphin AI", JOptionPane.QUESTION_MESSAGE);
                    if (userPrompt != null && !userPrompt.trim().isEmpty()) {
                        aiTab.askWithCustomPrompt(reqResp, userPrompt);
                    }
                });
                return List.of(askAiItem);
            }
            return null;
        }
    }

    // --- The Main UI Class ---
    class AITab extends JPanel {
        private JEditorPane chatArea;
        private JTextArea inputArea;
        private String currentRequestData = "";
        private StringBuilder chatHistory = new StringBuilder();

        public AITab() {
            setLayout(new BorderLayout());

            // Top: Chat Area (HTML)
            chatArea = new JEditorPane();
            chatArea.setContentType("text/html");
            chatArea.setEditable(false);
            chatArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            JScrollPane chatScroll = new JScrollPane(chatArea);

            // Bottom: User Input
            JPanel bottomPanel = new JPanel(new BorderLayout());
            inputArea = new JTextArea(3, 20);
            inputArea.setLineWrap(true);
            JScrollPane inputScroll = new JScrollPane(inputArea);

            JButton sendButton = new JButton("Ask AI");
            sendButton.addActionListener(e -> sendPrompt());

            bottomPanel.add(inputScroll, BorderLayout.CENTER);
            bottomPanel.add(sendButton, BorderLayout.EAST);

            add(chatScroll, BorderLayout.CENTER);
            add(bottomPanel, BorderLayout.SOUTH);

            addToHistory("SYSTEM", "Ready. Right-click a request and select 'Ask Local AI...' to begin.");
        }

        public void askWithCustomPrompt(HttpRequestResponse reqResp, String prompt) {
            this.currentRequestData = reqResp.request().toString();
            chatHistory.setLength(0);
            addToHistory("SYSTEM", "Target Request Loaded (" + reqResp.request().url() + ")");
            addToHistory("YOU", prompt);
            String fullPrompt = buildContextualPrompt(prompt);
            addToHistory("AI", "Thinking...");
            CompletableFuture.runAsync(() -> askOllama(fullPrompt));
        }

        public void loadRequest(HttpRequestResponse reqResp) {
            this.currentRequestData = reqResp.request().toString();
            chatHistory.setLength(0);
            addToHistory("SYSTEM", "Target Request Loaded (" + reqResp.request().url() + ")");
            addToHistory("SYSTEM", "I am listening.");
        }

        private void sendPrompt() {
            String userQuestion = inputArea.getText().trim();
            if (userQuestion.isEmpty()) return;
            addToHistory("YOU", userQuestion);
            inputArea.setText("");
            String fullPrompt = buildContextualPrompt(userQuestion);
            addToHistory("AI", "Thinking...");
            CompletableFuture.runAsync(() -> askOllama(fullPrompt));
        }

        private void addToHistory(String sender, String message) {
            if (sender.equals("AI") && chatHistory.toString().endsWith("Thinking...")) {
                int start = chatHistory.lastIndexOf("Thinking...");
                chatHistory.replace(start, start + 11, message);
            } else {
                chatHistory.append(sender).append(": ").append(message).append("\n\n");
            }

            // Convert to HTML and update UI
            String htmlContent = convertMarkdownToHtml(chatHistory.toString());
            SwingUtilities.invokeLater(() -> {
                chatArea.setText(htmlContent);
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            });
        }

        // --- FIXED MARKDOWN PARSER ---
        private String convertMarkdownToHtml(String rawText) {
            // 1. Escape HTML
            String safeText = rawText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

            // 2. User Styles (Colors for names)
            safeText = safeText.replace("YOU:", "<br><b><span style='color:#007bff'>YOU:</span></b>");
            safeText = safeText.replace("AI:", "<br><b><span style='color:#28a745'>AI:</span></b>");
            safeText = safeText.replace("SYSTEM:", "<br><b><span style='color:#dc3545'>SYSTEM:</span></b>");

            // 3. Code Blocks (Dark Mode Style)
            // Regex handles multiline code between triple backticks
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?s)```(?:[\\w]*\\s+)?(.*?)```").matcher(safeText);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String code = m.group(1);
                // CSS CHANGE: Dark background (#2b2b2b) and White text (#f8f8f2)
                String replacement = "<div style='background-color:#2b2b2b; color:#f8f8f2; padding:10px; border-radius:4px; font-family:monospace; margin:5px 0; white-space: pre-wrap;'>" + code + "</div>";
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            safeText = sb.toString();

            // 4. Inline Code (Single backtick) - Light Gray box with Dark Text
            safeText = safeText.replaceAll("`([^`]*)`", "<span style='background-color:#e0e0e0; color:#000000; font-family:monospace; padding: 3px; border-radius:3px;'>$1</span>");

            // 5. Bold
            safeText = safeText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

            // 6. Newlines
            safeText = safeText.replace("\n", "<br>");

            return "<html><body style='font-family:Segoe UI, sans-serif; font-size:12px;'>" + safeText + "</body></html>";
        }

        private String buildContextualPrompt(String question) {
            String safeRequest = currentRequestData.length() > 3000 ? currentRequestData.substring(0, 3000) + "...[TRUNCATED]" : currentRequestData;
            return "SYSTEM INSTRUCTIONS:\n" +
                    "You are an AI Logic Router. Output ONLY the response.\n" +
                    "LOGIC:\n" +
                    "1. IF input is 'stop', 'hello', 'just testing' -> Reply normally. No code.\n" +
                    "2. IF input asks for analysis/attacks -> Act as Red Team Hacker. Provide Python PoCs.\n" +
                    "*** RULE ***: Do NOT print 'CASE A'. Just print the reply.\n\n" +
                    "USER INPUT:\n" + question + "\n\n" +
                    "REQUEST DATA:\n" + safeRequest;
        }

        private void askOllama(String promptText) {
            try {
                String jsonSafePrompt = escapeJson(promptText);
                String jsonBody = "{\"model\": \"dolphin-mistral\", \"prompt\": \"" + jsonSafePrompt + "\", \"stream\": false}";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:11434/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String cleanResponse = extractResponseText(response.body());
                    addToHistory("AI", cleanResponse);
                } else {
                    addToHistory("SYSTEM", "Error " + response.statusCode());
                }
            } catch (Exception e) {
                addToHistory("SYSTEM", "Connection Error - " + e.getMessage());
            }
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
                    return content.replace("\\n", "\n").replace("\\r", "").replace("\\\"", "\"").replace("\\t", "\t").replace("\\\\", "\\").replace("\\u003c", "<").replace("\\u003e", ">");
                }
                return json;
            } catch (Exception e) { return json; }
        }
    }
}