# Sandbox image: compiles and runs ONE untrusted program per container (backend doc §5.2).
# Build (from javmotion-backend/, after `./mvnw -pl javmotion-tracer package`):
#   docker build -f docker/sandbox.Dockerfile -t javmotion-sandbox:21 .
FROM eclipse-temurin:21-jdk

# Non-root user; UID must match DockerCommandBuilder.SANDBOX_USER.
RUN groupadd --gid 10001 sandbox \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin sandbox \
 && mkdir -p /sandbox/src /sandbox/out /opt/javmotion

COPY docker/run.sh /sandbox/run.sh
COPY javmotion-tracer/target/javmotion-tracer-*.jar /opt/javmotion/tracer.jar
RUN chmod 755 /sandbox/run.sh && chmod 644 /opt/javmotion/tracer.jar

USER 10001:10001
WORKDIR /sandbox
