package localai;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class LocalLLMExtension implements BurpExtension {

    private CopilotTab copilotTab;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Bug Hunter's Copilot + Chat");

        // 1. Initialize the UI Tab
        this.copilotTab = new CopilotTab(api);
        api.userInterface().registerSuiteTab("Copilot", copilotTab);

        // 2. Register Context Menu
        api.userInterface().registerContextMenuItemsProvider(new ContextMenuProvider());

        api.logging().logToOutput("Bug Hunter's Copilot loaded. Chat interface enabled.");
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
}
