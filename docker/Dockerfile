FROM centos:7

ARG SNOWOWL_RPM_PACKAGE
ARG BUILD_TIMESTAMP
ARG VERSION
ARG GIT_REVISION

# Install java-11-openjdk as a pre requirement
RUN rpmkeys --import file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7 && \
    yum update --setopt=tsflags=nodocs -y -q -e 0 && \
    yum install --setopt=tsflags=nodocs -y -q -e 0 java-11-openjdk && \
    yum clean all

# Set JAVA_HOME environment variable
ENV JAVA_HOME /etc/alternatives/jre

RUN groupadd -g 1000 snowowl && \
    adduser -u 1000 -g 1000 -G 0 -d /usr/share/snowowl snowowl && \
    chmod 0775 /usr/share/snowowl && \
    chgrp 0 /usr/share/snowowl

# Install Snow Owl rpm package
WORKDIR /usr/share/snowowl
COPY ${SNOWOWL_RPM_PACKAGE} ${SNOWOWL_RPM_PACKAGE}
RUN rpm --install ${SNOWOWL_RPM_PACKAGE} && rm -f ${SNOWOWL_RPM_PACKAGE}

COPY --chown=1000:0 config/snowowl.yml /usr/share/snowowl/configuration/snowowl.yml
COPY --chown=1000:0 bin/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# Openshift overrides USER and uses ones with randomly uid>1024 and gid=0
# Allow ENTRYPOINT (and SO) to run even with a different user
RUN chgrp 0 /usr/local/bin/docker-entrypoint.sh && \
    chmod g=u /etc/passwd && \
    chmod 0775 /usr/local/bin/docker-entrypoint.sh

# Expose necessary ports used by Snow OWl
EXPOSE 2036 8080

LABEL org.label-schema.build-date="${BUILD_TIMESTAMP}" \
  org.label-schema.vcs-ref="${GIT_REVISION}" \
  org.label-schema.version="${VERSION}" \
  org.label-schema.license="Apache-2.0" \
  org.label-schema.name="Snow Owl OSS" \
  org.label-schema.schema-version="1.0" \
  org.label-schema.url="https://github.com/b2ihealthcare/snow-owl" \
  org.label-schema.usage="https://docs.b2i.sg/snow-owl" \
  org.label-schema.vcs-url="https://github.com/b2ihealthcare/snow-owl" \
  org.label-schema.vendor="B2i Healthcare" \
  org.opencontainers.image.created="${BUILD_TIMESTAMP}" \
  org.opencontainers.image.revision="${GIT_REVISION}" \
  org.opencontainers.image.version="${VERSION}" \
  org.opencontainers.image.licenses="Apache-2.0" \
  org.opencontainers.image.title="Snow Owl OSS" \
  org.opencontainers.image.url="https://github.com/b2ihealthcare/snow-owl" \
  org.opencontainers.image.documentation="https://docs.b2i.sg/snow-owl" \
  org.opencontainers.image.source="https://github.com/b2ihealthcare/snow-owl" \
  org.opencontainers.image.vendor="B2i Healthcare"

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
# Dummy overridable parameter parsed by entrypoint
CMD ["sowrapper"]
