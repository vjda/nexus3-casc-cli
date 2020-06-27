from typing import Dict, List, Union

from requests import Response

from nexuscasc.api.model.groovy_script import GroovyScriptRequestModel, GroovyScriptResponseModel
from nexuscasc.api.endpoint.base import BaseEndpoint
from nexuscasc.exception import NexusScriptApiError


class Script(BaseEndpoint):

    def create(self, name: str, content: str) -> bool:
        payload = GroovyScriptRequestModel(name=name, content=content).to_dict()
        r = self.client.request(method="POST", uri="/script", payload=payload)
        self.check_status_code(r)
        return r.status_code == 204

    def read(self, name: str) -> GroovyScriptRequestModel:
        r = self.client.request(method="GET", uri=f"/script/{name}")
        self.check_status_code(r)
        if r.status_code == 200:
            return GroovyScriptRequestModel(**r.json())

    def read_all(self, name: str) -> List[GroovyScriptRequestModel]:
        r = self.client.request(method="GET", uri=f"/script/{name}")
        self.check_status_code(r)
        if r.status_code == 200:
            scripts = list()
            for s in r.json():
                scripts.append(GroovyScriptRequestModel(**s))
            return scripts

    def update(self, name: str, content: str) -> bool:
        payload = GroovyScriptRequestModel(name=name, content=content).to_dict()
        r = self.client.request(method="PUT", uri=f"/script/{name}", payload=payload)
        self.check_status_code(r)
        return r.status_code == 204

    def delete(self, name: str) -> bool:
        r = self.client.request(method="DELETE", uri=f"/script/{name}")
        self.check_status_code(r)
        return r.status_code == 204

    def run(self, name: str, args: Dict = None) -> Union[GroovyScriptResponseModel, str]:
        r = self.client.request(method="POST", uri=f"/script/{name}/run", payload=args)
        self.check_status_code(r)
        if r.status_code == 200:
            response = r.json()
            return GroovyScriptResponseModel(**response)
        else:
            return r.text

    @staticmethod
    def check_status_code(response: Response):
        code = response.status_code
        path_url = response.request.path_url
        if code == 400:
            raise NexusScriptApiError("Bad parameters", path_url, code)
        elif code == 401:
            raise NexusScriptApiError("Unauthorized", path_url, code)
        elif code == 403:
            raise NexusScriptApiError(f"Forbidden: {response.text}", path_url, code)
        elif code == 404:
            raise NexusScriptApiError("Script not found", path_url, code)
        if code == 500 and "ORecordDuplicatedException" in response.text:
            raise NexusScriptApiError("Script already exists", path_url, code)
        elif code >= 400:
            raise NexusScriptApiError(f"Unknown exception: {response.text}", path_url, code)
