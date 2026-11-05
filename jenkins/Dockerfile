FROM docker:29-cli AS docker-cli

FROM jenkins/jenkins:lts-jdk21

USER root

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-buildx \
    /usr/local/libexec/docker/cli-plugins/docker-buildx

RUN docker --version && docker buildx version

USER jenkins

RUN jenkins-plugin-cli --plugins \
    "workflow-aggregator git credentials-binding"
