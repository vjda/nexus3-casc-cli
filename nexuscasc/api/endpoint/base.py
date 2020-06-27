from nexuscasc.api.client import NexusApiClient


class BaseEndpoint:
    client: NexusApiClient

    def __init__(self, client: NexusApiClient):
        self.client = client
