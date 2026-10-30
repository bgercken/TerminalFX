# Same Maven build base as the JediTermFX container; GUI layer can be reused.
FROM docker.io/library/maven:3.9-eclipse-temurin-17
USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb x11vnc novnc websockify openbox x11-utils xdotool \
    libgtk-3-0 libgl1 libgl1-mesa-dri libxtst6 libxi6 libxrender1 \
    fonts-dejavu-core curl procps \
    && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y --no-install-recommends libgtk2.0-0 \
    && rm -rf /var/lib/apt/lists/*
ARG JAVA8_URL
RUN test -n "$JAVA8_URL" && mkdir -p /opt/java8 \
    && curl -fL --retry 3 "$JAVA8_URL" -o /tmp/java8.tar.gz \
    && tar -xzf /tmp/java8.tar.gz -C /opt/java8 --strip-components=1 \
    && rm /tmp/java8.tar.gz \
    && test -f /opt/java8/jre/lib/ext/jfxrt.jar
# WebView initializes JavaFX media support even for terminal-only HTML.
RUN apt-get update && apt-get install -y --no-install-recommends libasound2t64 \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/opt/java8
ENV PATH="/opt/java8/bin:${PATH}" DISPLAY=:99 SHELL=/bin/bash TERM=xterm-256color LANG=C.UTF-8
COPY entrypoint.sh /usr/local/bin/terminalfx-test
RUN chmod +x /usr/local/bin/terminalfx-test
EXPOSE 6080
ENTRYPOINT ["/usr/local/bin/terminalfx-test"]
