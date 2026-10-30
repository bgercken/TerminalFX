package com.terminalfx;

import com.terminalfx.config.DefaultTabNameGenerator;
import com.terminalfx.config.TabNameGenerator;
import com.terminalfx.config.TerminalConfig;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import netscape.javascript.JSObject;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Optional tab wrapper around the transport-independent TerminalView. */
public class TerminalTab extends Tab {
    private final TerminalView terminalView;
    private final TabNameGenerator nameGenerator;
    private Path terminalPath;

    public TerminalTab() { this(new TerminalConfig(), new DefaultTabNameGenerator(), null); }
    public TerminalTab(String text) { this(); setText(text); }
    public TerminalTab(String text, Node content) { this(text); }

    /** terminalPath is retained for source compatibility; no local process is started. */
    public TerminalTab(TerminalConfig config, TabNameGenerator names, Path terminalPath) {
        this.nameGenerator = names == null ? new DefaultTabNameGenerator() : names;
        this.terminalPath = terminalPath;
        terminalView = new TerminalView(config);
        setContent(terminalView);
        setText(nameGenerator.next());
        setOnClosed(event -> destroy());
        MenuItem newTab = new MenuItem("New Tab");
        newTab.setOnAction(this::newTerminal);
        MenuItem closeTab = new MenuItem("Close");
        closeTab.setOnAction(this::closeTerminal);
        setContextMenu(new ContextMenu(newTab, closeTab));
    }

    public TerminalView getTerminalView() { return terminalView; }
    public void print(String text) { terminalView.append(text); }
    public void setOnInput(Consumer<String> handler) { terminalView.setOnInput(handler); }
    public void focusCursor() { terminalView.focusCursor(); }
    public boolean isReady() { return terminalView.isReady(); }
    public JSObject getWindow() { return terminalView.getWindow(); }
    public TerminalConfig getTerminalConfig() { return terminalView.getTerminalConfig(); }
    public void updatePrefs(TerminalConfig config) { terminalView.updatePrefs(config); }
    public void setTerminalConfig(TerminalConfig config) { updatePrefs(config); }
    public TabNameGenerator getTabNameGenerator() { return nameGenerator; }
    public Path getTerminalPath() { return terminalPath; }
    public void setTerminalPath(Path path) { terminalPath = path; }
    public void destroy() { terminalView.close(); }

    public void newTerminal(ActionEvent... event) {
        if (getTabPane() == null) return;
        TerminalTab tab = new TerminalTab(getTerminalConfig(), nameGenerator, terminalPath);
        getTabPane().getTabs().add(tab);
        getTabPane().getSelectionModel().select(tab);
    }

    public void closeTerminal(ActionEvent... event) {
        if (getTabPane() != null) getTabPane().getTabs().remove(this);
        destroy();
    }
}
