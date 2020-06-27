from pathlib import Path
from typing import Dict

import yaml

from nexuscasc.exception import NexusCascError


class YamlLoader:

    @staticmethod
    def check_if_file_exists(file_path: Path):
        """Check if yaml file exists.

        Raises:
            NexusCascError: If yaml file is not found
        """
        if not file_path.exists() and file_path.is_file():
            raise NexusCascError(message=f"Yaml file not found in {file_path}")

    @staticmethod
    def get_content_from_file(file_path: Path) -> Dict[str, object]:
        """Return the whole yaml content read from a file.

        Returns:
            {object} -- It could be a dictionary or a list of dictionaries with the yaml keys and values
        """
        YamlLoader.check_if_file_exists(file_path)
        with file_path.open() as f:
            return yaml.safe_load(f)

    @staticmethod
    def get_content_from_string(yaml_string: str) -> Dict[str, object]:
        """Return the whole yaml content read from a string.

        Returns:
            {object} -- It could be a dictionary or a list of dictionaries with the yaml keys and values
        """
        return yaml.safe_load(yaml_string)
