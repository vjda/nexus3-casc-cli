from collections import defaultdict
from pathlib import Path
from typing import List, Dict

from nexuscasc.config.yaml_loader import YamlLoader
from nexuscasc.logger import Logger
from nexuscasc.exception import NexusCascError
from nexuscasc.config.constant import DEFAULT_CONFIG_YAML


class ConfigHandler(YamlLoader):
    config: Dict[str, object] = dict()

    def load_config_from_strings(self, yaml_strings: List[str]):
        default_yaml_content = self.get_content_from_file(Path(DEFAULT_CONFIG_YAML))
        result = default_yaml_content
        for yaml_str in yaml_strings:
            result = self.merge_copy_dicts(result, self.get_content_from_string(yaml_str))
        self.config = result

    def load_config_from_path(self, path: Path):
        default_yaml_path = Path(DEFAULT_CONFIG_YAML)
        yaml_paths = list()
        yaml_paths.append(default_yaml_path)
        if path.is_file():
            yaml_paths.append(path)
        elif path.is_dir():
            yaml_paths = list(path.glob("*.yaml"))
            yaml_paths.extend(path.glob("*.yml"))
        else:
            raise NexusCascError(f"Path is not a directory or a file: {path}")

        self.config = self._combine_yaml_files(yaml_paths)

    def _combine_yaml_files(self, paths: List[Path]) -> Dict[str, object]:
        result = self.get_content_from_file(paths.pop(0))
        for p in paths:
            result = self.merge_copy_dicts(result, self.get_content_from_file(p))
        return result

    @staticmethod
    def merge_copy_dicts(dict1: Dict, dict2: Dict) -> Dict[str, object]:
        merged_dict: Dict = defaultdict(dict)
        merged_dict.update(dict1)
        for key, value in dict2.items():
            if value is None and merged_dict[key] is not None:
                # it continues when user has set an empty key and defaults is not empty
                continue
            if type(value) is list:
                if type(merged_dict[key]) is not list:
                    merged_dict[key] = value
                else:
                    merged_dict[key].extend(value)
            elif type(value) is dict:
                if merged_dict[key] is None or merged_dict[key] == dict():
                    merged_dict[key].update(value)
                else:
                    merged_dict[key] = ConfigHandler.merge_copy_dicts(merged_dict[key], value)
            else:
                merged_dict[key] = value

        return dict(merged_dict)

    def get_config_key(self, key: str) -> object:
        try:
            result = self.config
            for k in key.split('.'):
                result = result[k]
            return result
        except KeyError:
            Logger.warning(f"key {key} does not found in yaml_loader.py file")
            return None
