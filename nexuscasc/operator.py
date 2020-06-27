import json
from typing import List

import typer

from nexuscasc import Logger
from nexuscasc.api import NexusApiClient, Configuration
from nexuscasc.api.endpoint import Script, CheckStatus
from nexuscasc.api.model import GroovyScriptResponseModel
from nexuscasc.config import ConfigHandler, GroovyScriptHandler, GroovyScriptConfig
from nexuscasc.exception import NexusCascError


class Operator:
    nexus_client: NexusApiClient
    groovy_scripts: List[GroovyScriptConfig] = GroovyScriptHandler().scripts

    def __init__(self, config: ConfigHandler):
        self.config_handler = config
        self.connect_nexus_server()

    def connect_nexus_server(self):
        # Check if connection successful
        base_url = str(self.config_handler.get_config_key('nexus.baseUrl'))
        username = str(self.config_handler.get_config_key('nexus.defaults.adminUser'))
        password = str(self.config_handler.get_config_key('nexus.adminPassword'))
        api_config = Configuration(base_url, username, password)
        self.nexus_client = NexusApiClient(api_config)

        Logger.debug(f"Trying to connect to {base_url} with nexus.defaults.adminUser and nexus.adminPassword")
        success = CheckStatus(self.nexus_client).check_connection()

        if not success:
            Logger.warning(f"Cannot connect. Retrying with password value at nexus.defaults.adminPassword")
            password = str(self.config_handler.get_config_key('nexus.defaults.adminPassword'))
            api_config = Configuration(base_url, username, password)
            self.nexus_client = NexusApiClient(api_config)
            success = CheckStatus(self.nexus_client).check_connection()
            Logger.debug("Connection OK!")

        if not success:
            raise NexusCascError("Neither nexus.adminPassword nor nexus.defaults.adminPassword are correct")

    def execute_scripts(self):
        for script in self.groovy_scripts:
            Logger.debug(f"Loading script '{script.name}' from groovy file")
            self._create_or_update_script(script.name)
            self._execute_script(script.name, script.send_args)
        Logger.debug("All scripts executed")

    def _create_or_update_script(self, name: str):
        groovy_script_text = GroovyScriptHandler.read_script_content(name)
        script_api = Script(client=self.nexus_client)
        try:
            script_api.read(name)
            Logger.debug(f"Updating script '{name}' on Nexus")
            script_api.update(name, groovy_script_text)
        except NexusCascError:
            Logger.debug(f"Creating script '{name}' on Nexus")
            script_api.create(name, groovy_script_text)

    def _execute_script(self, name: str, send_args: bool):
        script_api = Script(client=self.nexus_client)
        Logger.debug(f"Running script '{name}' on Nexus")
        if send_args:
            script_response = script_api.run(name, args=self.config_handler.config)
        else:
            script_response = script_api.run(name)

        if isinstance(script_response, GroovyScriptResponseModel):
            if isinstance(script_response.result, dict) and script_response.result["error"]:
                message = typer.style(json.dumps(script_response.to_dict()), fg=typer.colors.RED, bold=True)
                Logger.error(message)
            else:
                Logger.info(json.dumps(script_response.to_dict()))
        else:
            Logger.info(script_response)
