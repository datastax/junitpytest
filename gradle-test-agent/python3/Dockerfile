# Switch to root user for installing packages
USER root

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y python3.8 python3-pip python3.8-dev virtualenv git && \
    pip3 install --upgrade pip && \
    apt-get clean

# Tribute to Apache Cassandra dtests/ccm
RUN curl -LfsSo /tmp/ant.tar.gz https://downloads.apache.org//ant/binaries/apache-ant-1.10.8-bin.tar.gz && \
    mkdir -p /opt/ant && \
    cd /opt/ant && \
    tar -xf /tmp/ant.tar.gz --strip-components=1; \
    rm -f /tmp/ant.tar.gz

ENV PATH=/opt/ant/bin:$PATH

# Switch back to application user
USER gradle:staff
