FROM python:3.8-alpine

ARG CLI_DIR=/opt/cli/
ARG USER_GROUP_ID=nexus3casc

RUN pip install pipenv

RUN mkdir -p ${CLI_DIR} && \
    adduser -s /bin/sh -D ${USER_GROUP_ID} && \
    chown ${USER_GROUP_ID}:${USER_GROUP_ID} ${CLI_DIR}

WORKDIR ${CLI_DIR}

USER ${USER_GROUP_ID}

COPY nexus3casc.py ${CLI_DIR}
COPY nexuscasc ${CLI_DIR}/nexuscasc
COPY Pipfile* ${CLI_DIR}
COPY resources ${CLI_DIR}/resources

RUN pipenv install --deploy --ignore-pipfile

ENTRYPOINT ["pipenv", "run", "nexus3casc"]
