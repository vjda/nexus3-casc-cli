from .base import BaseEndpoint


class CheckStatus(BaseEndpoint):

    def check_connection(self) -> bool:
        r = self.client.request(method="GET", uri="/status/check")
        return r.status_code == 200
