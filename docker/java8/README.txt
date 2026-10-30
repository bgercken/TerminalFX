TerminalFX Java 8 serial-console display
======================================

From the repository root, with Docker Desktop or Podman running:

    ./docker-test.sh

Open http://localhost:6080/vnc.html?autoconnect=1&resize=remote after startup.
Ctrl-C stops/removes the container. No Java installation is needed on macOS.
Only host loopback is published; this local test desktop has no VNC password.
The example displays sample terminal output. It does not start a local shell
or connect to a serial device. Your application supplies the serial transport.

Automated validation:

    SMOKE_TEST=1 ./docker-test.sh

The smoke test uses actual Java 8 and JavaFX 8 to exercise terminal rendering,
scrolling, full-screen/alternate-screen sequences, and input/reply callbacks.
It verifies all application class files target Java 8 and opens the example
window, then checks noVNC HTTP delivery. No hardware is needed for these tests.

The Dockerfile extends the same Maven base used for the JediTermFX test and
reuses its GUI package layer when cached. It adds Java 8 with bundled JavaFX,
GTK 2, and the audio library that JavaFX WebView initializes. All Java builds
and runtime execution happen inside this image. The host source is mounted
read-only and copied to the container, excluding stale target and .git files.

Maven dependencies and plugins are forced through Maven Central only by the
included settings file. PTY, JNA, and serial-library dependencies are removed.
The base image, Linux packages, and JDK distribution use the container registry,
Ubuntu repositories, and Azul CDN respectively; the Central restriction applies
to Maven artifacts. No JetBrains repository is configured or needed.

Options:

    CONTAINER_ENGINE=podman TEST_PORT=6081 ./docker-test.sh
    ./docker-test.sh --help

The native host architecture is selected by default. A separate persistent
terminalfx-java8-m2 container volume caches Maven downloads. The pinned Azul
JDK 8 with JavaFX download can be overridden with JAVA8_URL. Initial downloads
are reused on subsequent launches.

Embedding:
- Construct TerminalView on the JavaFX application thread and add it to a layout.
- Feed decoded serial output through append(String), from any thread.
- Use setOnInput(Consumer<String>) to forward keystrokes and terminal replies
  through your application's ordered serial writer, without blocking the UI.
- Use an incremental decoder/persistent Reader so byte chunks do not split
  multibyte characters. Match the switch's character encoding.
- rowsProperty/columnsProperty expose size; serial does not negotiate it by itself.
- close() releases display resources. Your application owns the serial connection.

See the repository README.adoc for the embedding example and compatibility notes.
