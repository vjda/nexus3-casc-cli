from dataclasses import dataclass
from typing import Dict

from requests import Response, request


@dataclass
class Configuration:
    base_url: str
    username: str = 'admin'
    password: str = 'admin123'
    timeout: int = 30


class NexusApiClient:
    api_config: Configuration
    prefix: str = "/service/rest/v1"

    def __init__(self, config: Configuration):
        self.api_config = config

    def request(self, method: str, uri: str, auth: bool = True, payload: Dict = None) -> Response:
        args = {
            "method": method.upper(),
            "url": "/".join(map(lambda x: str(x).strip('/'), [self.api_config.base_url, self.prefix, uri])),
            "timeout": self.api_config.timeout
        }

        if auth:
            args["auth"] = (self.api_config.username, self.api_config.password)

        if method.upper() != "GET":
            args["headers"] = {'Content-type': 'application/json', 'Accept': 'application/json'}

        if payload is not None:
            args["json"] = payload

        return request(**args)
