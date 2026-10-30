package com.terminalfx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminalfx.annotation.WebkitCall;
import com.terminalfx.config.TerminalConfig;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.concurrent.Worker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * JavaFX terminal emulator with no process, PTY, or serial-port dependency.
 * Construct on the FX thread. append() may be called from a transport reader
 * thread; it preserves chunk order and queues output until hterm is ready.
 * Input callbacks run on the FX thread; hand blocking serial writes to your
 * application's ordered writer. Decode serial bytes incrementally before append.
 */
public class TerminalView extends StackPane implements AutoCloseable {
    private final WebView webView = new WebView();
    private final ReadOnlyBooleanWrapper ready = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper columns = new ReadOnlyIntegerWrapper(80);
    private final ReadOnlyIntegerWrapper rows = new ReadOnlyIntegerWrapper(24);
    private final Object outputLock = new Object();
    private final ArrayDeque<String> pendingOutput = new ArrayDeque<>();
    private boolean initialized;
    private boolean flushScheduled;
    private boolean closed;
    private TerminalConfig terminalConfig;
    private Consumer<String> onInput = input -> { };

    public TerminalView() { this(new TerminalConfig()); }

    public TerminalView(TerminalConfig config) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Construct TerminalView on the JavaFX application thread");
        }
        terminalConfig = Objects.requireNonNull(config, "config");
        getChildren().add(webView);
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
            if (isClosed()) return;
            if (state == Worker.State.SUCCEEDED) {
                JSObject window = getWindow();
                window.setMember("app", this);
                window.call("initializeTerminal");
            } else if (state == Worker.State.FAILED) {
                webView.getEngine().getLoadWorker().getException().printStackTrace();
            }
        });
        webView.getEngine().load(TerminalView.class.getResource("/hterm.html").toExternalForm());
    }

    /** Feed decoded terminal output, including escape sequences. Safe off the FX thread. */
    public void append(String text) {
        Objects.requireNonNull(text, "text");
        synchronized (outputLock) {
            if (closed) throw new IllegalStateException("Terminal is closed");
            if (text.isEmpty()) return;
            pendingOutput.addLast(text);
            if (!initialized || flushScheduled) return;
            flushScheduled = true;
        }
        runOnFxThread(this::flushOutput);
    }

    public void print(String text) { append(text); }
    public void clear() { append("\033[2J\033[H"); }

    private void flushOutput() {
        while (true) {
            String text;
            synchronized (outputLock) {
                if (closed || !initialized || pendingOutput.isEmpty()) {
                    flushScheduled = false;
                    return;
                }
                text = pendingOutput.removeFirst();
            }
            ((JSObject) getWindow().eval("t.io")).call("print", text);
        }
    }

    /** Receives keystrokes, paste, and terminal replies; no connection is opened here. */
    public void setOnInput(Consumer<String> handler) {
        onInput = Objects.requireNonNull(handler, "handler");
    }

    @WebkitCall
    public void command(String input) {
        if (!isClosed()) onInput.accept(input);
    }

    @WebkitCall
    public void onTerminalInit() { /* The WebView is already attached to this pane. */ }

    @WebkitCall
    public void onTerminalReady() {
        synchronized (outputLock) {
            if (closed) return;
            initialized = true;
            flushScheduled = true;
        }
        flushOutput();
        ready.set(true);
    }

    @WebkitCall
    public void resizeTerminal(int columnCount, int rowCount) {
        columns.set(columnCount);
        rows.set(rowCount);
    }

    @WebkitCall
    public String getPrefs() {
        try {
            return new ObjectMapper().writeValueAsString(terminalConfig);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize terminal preferences", exception);
        }
    }

    public TerminalConfig getTerminalConfig() { return terminalConfig; }

    public void updatePrefs(TerminalConfig config) {
        Objects.requireNonNull(config, "config");
        runOnFxThread(() -> {
            if (isClosed()) return;
            terminalConfig = config;
            if (isReady()) getWindow().call("updatePrefs", getPrefs());
        });
    }

    @WebkitCall
    public void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void focusCursor() {
        runOnFxThread(() -> {
            if (!isClosed() && isReady()) {
                webView.requestFocus();
                ((JSObject) getWindow().eval("t")).call("focus");
            }
        });
    }

    /** Advanced access; call only on the FX thread, after readiness. */
    public JSObject getWindow() {
        return (JSObject) webView.getEngine().executeScript("window");
    }

    public boolean isReady() { return ready.get(); }
    public ReadOnlyBooleanProperty readyProperty() { return ready.getReadOnlyProperty(); }
    public int getColumns() { return columns.get(); }
    public ReadOnlyIntegerProperty columnsProperty() { return columns.getReadOnlyProperty(); }
    public int getRows() { return rows.get(); }
    public ReadOnlyIntegerProperty rowsProperty() { return rows.getReadOnlyProperty(); }

    private boolean isClosed() {
        synchronized (outputLock) { return closed; }
    }

    /** Releases WebView resources; idempotent and safe before initialization completes. */
    @Override
    public void close() {
        synchronized (outputLock) {
            if (closed) return;
            closed = true;
            pendingOutput.clear();
        }
        runOnFxThread(() -> {
            ready.set(false);
            onInput = input -> { };
            webView.getEngine().load(null);
            getChildren().clear();
        });
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }
}
