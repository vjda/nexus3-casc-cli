from dataclasses import dataclass
from typing import Dict, List
from nexuscasc.config.yaml_loader import YamlLoader
from nexuscasc.exception import NexusCascError
from .constant import GROOVY_CONFIG_YAML
from pathlib import Path
import os
from nexuscasc.config.constant import GROOVY_SCRIPTS_PATH


@dataclass
class GroovyScriptConfig:
    name: str
    send_args: bool = True
    parse_response: bool = True


class GroovyScriptHandler(YamlLoader):
    scripts: List[GroovyScriptConfig] = list()

    def __init__(self):
        groovy_yaml_file = Path(GROOVY_CONFIG_YAML)
        content: Dict = self.get_content_from_file(groovy_yaml_file)
        if "scripts" in content.keys():
            for sc in content.get("scripts"):
                self.scripts.append(GroovyScriptConfig(**sc))
        else:
            raise NexusCascError(f"{groovy_yaml_file} does not have a key 'scripts'")

    @staticmethod
    def read_script_content(script_name: str):
        groovy_file_path = Path(os.path.join(GROOVY_SCRIPTS_PATH, f"{script_name}.groovy"))
        with groovy_file_path.open() as file:
            return str.join('', file.readlines())
