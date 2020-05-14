# Switch to root user for installing packages
USER root

# Install JDK8
RUN set -eux; \
    curl -LfsSo /tmp/openjdk-8.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk-8.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/java/openjdk-8; \
    cd /opt/java/openjdk-8; \
    tar -xf /tmp/openjdk-8.tar.gz --strip-components=1; \
    rm -rf /tmp/openjdk-8.tar.gz

ENV JDK8=/opt/java/openjdk-8 \
    JAVA8_HOME=/opt/java/openjdk-8 \
    JAVA11_HOME=/opt/java/openjdk

# Switch back to application user
USER gradle:staff
