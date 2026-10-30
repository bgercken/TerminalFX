package com.terminalfx;

import com.sun.javafx.webkit.WebConsoleListener;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** Container integration checks against the actual JavaFX 8 WebView and hterm. */
public class TerminalDisplaySmoke extends Application {
    @Override
    public void start(Stage stage) {
        WebConsoleListener.setDefaultListener((view, message, line, source) ->
                System.err.println("WebView: " + message + " (line " + line + ")"));
        TerminalView terminal = new TerminalView();
        terminal.append("queued before ready");
        List<String> outbound = new ArrayList<>();
        terminal.setOnInput(outbound::add);
        terminal.readyProperty().addListener((observable, oldValue, ready) -> {
            if (!ready) return;
            try {
                require(row(terminal, 0).equals("queued before ready"), "output queued before readiness");
                require(Boolean.TRUE.equals(terminal.getWindow().eval("t.scrollOnOutput_")),
                        "new output follows the bottom by default");
                terminal.clear();
                terminal.append("Hello\033[2;1Hworld\033[1;1HX");
                require(row(terminal, 0).equals("Xello"), "cursor overwrite for full-screen redraws");
                require(row(terminal, 1).equals("world"), "cursor placement");
                terminal.append("\033[2;1H\033[2K\033[31mRED\033[0m");
                require(row(terminal, 1).equals("RED"), "erase line");
                require(Boolean.TRUE.equals(terminal.getWindow().eval(
                        "Array.prototype.some.call(t.getRowNode(1).querySelectorAll('span'), "
                        + "function(s) { return s.textContent === 'RED' && s.style.color !== ''; })")),
                        "SGR foreground applied to DOM");
                terminal.append("\033[3;1Hcaf\u00e9 \u03a9");
                require(row(terminal, 2).equals("caf\u00e9 \u03a9"), "Unicode");
                terminal.append("\033[4;1Hsplit\033[");
                terminal.append("4;1HZ");
                require(row(terminal, 3).equals("Zplit"), "sequence split across chunks");
                terminal.append("\033[5;1H\033(0lqk\033(B");
                require(row(terminal, 4).equals("\u250c\u2500\u2510"), "VT100 line drawing");
                terminal.append("\033[?1049h\033[Halternate screen");
                require(row(terminal, 0).equals("alternate screen"), "alternate screen");
                terminal.append("\033[?1049l");
                require(row(terminal, 0).equals("Xello"), "normal screen restored");
                terminal.append("\033[2;4H\033[6n");
                require(outbound.contains("\033[2;4R"), "cursor report returned to transport");
                terminal.getWindow().eval("t.io.onVTKeystroke('\\u001b[A')");
                require(outbound.contains("\033[A"), "keyboard escape sequence forwarded");
                int height = ((Number) terminal.getWindow().eval("t.screenSize.height")).intValue();
                terminal.append("\033[H");
                for (int i = 0; i < height + 5; i++) terminal.append("line-" + i + "\r\n");
                require(((Number) terminal.getWindow().eval("t.scrollbackRows_.length")).intValue() > 0,
                        "output scrolls into history");
                require(terminal.getRows() > 0 && terminal.getColumns() > 0, "terminal size callback");
                com.terminalfx.config.TerminalConfig preferences = new com.terminalfx.config.TerminalConfig();
                preferences.setScrollOnOutput(false);
                terminal.updatePrefs(preferences);
                terminal.append("\033[H");
                new Thread(() -> {
                    terminal.append("worker output");
                    Platform.runLater(() -> {
                        // hterm delivers preference observers on its next browser timer tick.
                        PauseTransition pause = new PauseTransition(Duration.millis(200));
                        pause.setOnFinished(event -> finish(terminal));
                        pause.play();
                    });
                }, "serial-reader-test").start();
            } catch (Throwable failure) { fail(failure); }
        });
        stage.setScene(new Scene(terminal, 900, 600));
        stage.setTitle("TerminalFX Java 8 display smoke test");
        stage.show();
    }

    private static void finish(TerminalView terminal) {
        try {
            require(Boolean.FALSE.equals(terminal.getWindow().eval("t.scrollOnOutput_")),
                    "auto-follow preference can be disabled");
            require(Boolean.TRUE.equals(terminal.getWindow().eval(
                    "t.screen_.rowsArray[0].textContent.indexOf('worker output') === 0")),
                    "output from background serial reader");
            terminal.close();
            terminal.close();
            require(!terminal.isReady(), "idempotent close");
            TerminalView closedBeforeReady = new TerminalView();
            closedBeforeReady.append("pending");
            closedBeforeReady.close();
            System.out.println("PASS: JavaFX 8 display, queued/background output, VT cursor/erase/colors, Unicode, chunked sequences, line drawing, alternate screen, scrolling, input/reply callbacks, resize, preferences, close.");
            Platform.exit();
            System.exit(0);
        } catch (Throwable failure) { fail(failure); }
    }

    private static String row(TerminalView terminal, int index) {
        return terminal.getWindow().eval("t.getRowText(" + index + ")").toString().trim();
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    private static void fail(Throwable failure) {
        failure.printStackTrace();
        System.exit(1);
    }
    public static void main(String[] args) { launch(args); }
}
