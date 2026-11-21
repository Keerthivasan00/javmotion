# Step B0 spike: does JDI tracing work inside the sandbox limits?
# Build: docker build -t jm-jdi-spike .
# Run:   ./docker-test.sh
FROM eclipse-temurin:21-jdk
RUN useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin sandbox
COPY --chown=root:root src /spike/src
COPY --chown=root:root samples /spike/samples
COPY --chown=root:root run-all.sh /spike/run-all.sh
RUN chmod 755 /spike/run-all.sh
USER 10001:10001
ENTRYPOINT ["/spike/run-all.sh"]
