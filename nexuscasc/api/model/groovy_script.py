import json
from json import JSONDecodeError

from typing import Dict, Union
from dataclasses import dataclass, asdict


@dataclass
class GroovyScriptRequestModel(object):
    name: str
    content: str
    type: str = "groovy"

    def to_dict(self):
        return asdict(self)


@dataclass
class GroovyScriptResponseModel(object):
    name: str
    result: Union[Dict, str]

    def __init__(self, name: str, result: str):
        self.name = name
        try:
            self.result = json.loads(result)
        except JSONDecodeError:
            self.result = result

    def to_dict(self):
        return asdict(self)
