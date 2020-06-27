#!/usr/bin/env python3

import os
import time
from enum import Enum
from pathlib import Path

import typer

from nexuscasc.config import ConfigHandler, K8sConfigHandler
from nexuscasc.exception import NexusCascError
from nexuscasc import Logger, Operator

app = typer.Typer()


class LogLevel(str, Enum):
    error = "ERROR"
    info = "INFO"
    debug = "DEBUG"


class K8sResources(str, Enum):
    configmaps = "configmaps"
    secrets = "secrets"
    both = "both"


@app.callback()
def global_options(
        log_level: LogLevel = typer.Option(
            LogLevel.info,
            envvar="NEXUS3_CASC_LOG_LEVEL",
            show_envvar=True,
            show_default=True,
            help="Set logging level")
):
    os.environ["NEXUS3_CASC_LOG_LEVEL"] = log_level.value


@app.command()
def from_path(
        config: Path = typer.Option(
            ...,
            envvar="NEXUS3_CASC_CONFIG_PATH",
            exists=True,
            file_okay=True,
            dir_okay=True,
            writable=False,
            readable=True,
            resolve_path=True,
            show_envvar=True,
            help="Path or directory to load YAML file(s)")
):
    """
    Read config from YAML file(s) and inject it into a Nexus 3 instance server.

    CONFIG can be an absolute or relative path to a YAML file or a directory.
    If it is a directory every YAML file found in that path will be merged.
    """
    Logger.debug(f"Loading config from path '{config}'")
    conf_handler = ConfigHandler()
    conf_handler.load_config_from_path(path=config)
    Operator(conf_handler).execute_scripts()


@app.command()
def from_k8s(
        namespace: str = typer.Option(
            ...,
            envvar="NEXUS3_CASC_K8S_NAMESPACE",
            show_envvar=True,
            metavar="VALUE",
            help="Kubernetes namespace"),
        label: str = typer.Option(
            ...,
            envvar="NEXUS3_CASC_K8S_LABEL",
            show_envvar=True,
            metavar="KEY",
            help="Label to filter configmaps or secrets"),
        label_value: str = typer.Option(
            None,
            envvar="NEXUS3_CASC_K8S_LABEL_VALUE",
            show_envvar=True,
            metavar="VALUE",
            help="Label value to filter configmaps or secrets"),
        resource: K8sResources = typer.Option(
            K8sResources.both,
            envvar="NEXUS3_CASC_K8S_RESOURCE",
            show_envvar=True,
            metavar="TYPE",
            help="Type of resource to search for"),
        local: bool = typer.Option(
            False,
            '--local',
            help="Connect to kubernetes cluster using kubeconfig"),
        watch: bool = typer.Option(
            False,
            '--watch',
            help="Watch resources to refresh their changes"),
        refresh_period: int = typer.Option(
            30,
            min=0,
            envvar="NEXUS3_CASC_K8S_REFRESH_PERIOD",
            metavar="SECONDS",
            show_envvar=True,
            help="Seconds between searches to refresh changes")
):
    """
    Fetch config from either configmaps, secrets or both in a kubernetes namespace
    and use them to inject the merged configuration into a Nexus 3 instance server.
    """
    k8s = K8sConfigHandler(local)
    conf_handler = ConfigHandler()
    execute_at_least_once = True

    while execute_at_least_once or watch:
        execute_at_least_once = False
        yaml_strings = list()
        resources = list()

        if resource in [K8sResources.both, K8sResources.configmaps]:
            Logger.debug(f"Fetching YAML files from configmap keys")
            configmaps = k8s.find_config_maps(namespace, label, label_value)
            yaml_strings.extend(k8s.extract_yaml_strings_from_resources(configmaps))
            resources.extend(configmaps)

        if resource in [K8sResources.both, K8sResources.secrets]:
            Logger.debug(f"Fetching YAML files from secret keys")
            secrets = k8s.find_secrets(namespace, label, label_value)
            yaml_strings.extend(k8s.extract_yaml_strings_from_resources(secrets))
            resources.extend(secrets)

        if k8s.any_resource_has_changed(resources):
            Logger.debug("New config detected")
            Logger.debug("Loading config from YAML strings")
            conf_handler.load_config_from_strings(yaml_strings)
            try:
                Operator(conf_handler).execute_scripts()
            except Exception as exc:
                Logger.fatal(f"There was an error executing scripts: {exc}")
                # Clear the watch list to retry applying the configuration changes in the next iteration
                k8s.watch_list.clear()
        else:
            Logger.debug("No config changes detected")

        Logger.debug(f"Waiting for {refresh_period} seconds before searching for changes")
        time.sleep(refresh_period)


if __name__ == "__main__":
    try:
        app()
    except NexusCascError as err:
        Logger.fatal(f"There was an error: {err.message}")
    except Exception as ex:
        Logger.fatal(f"Unexpected error: {ex}")
