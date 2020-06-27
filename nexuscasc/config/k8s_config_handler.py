import base64
import re
from dataclasses import dataclass
from enum import Enum
from typing import Union, List

from kubernetes import client, config
from kubernetes.client import V1ConfigMapList, V1SecretList, CoreV1Api, V1Secret, V1ConfigMap

from nexuscasc.logger import Logger


class ResourceType(Enum):
    SECRET, CONFIGMAP = range(2)


@dataclass
class WatchedResource:
    name: str
    version: str
    type: ResourceType


class K8sConfigHandler:
    v1: CoreV1Api = None
    watch_list: List[WatchedResource] = list()

    def __init__(self, local: bool = False):
        if local:
            config.load_kube_config()
        else:
            config.load_incluster_config()
        self.v1 = client.CoreV1Api()

    @staticmethod
    def filter_resources(
            resources: Union[V1ConfigMapList, V1SecretList],
            label_value: str = None
    ) -> List[Union[V1ConfigMap, V1Secret]]:
        matches = list()
        for res in resources.items:
            if label_value is None:
                matches.append(res)
            elif len(list(filter(lambda x: res.metadata.labels[x] == label_value, res.metadata.labels.keys()))) > 0:
                matches.append(res)
        return matches

    def find_config_maps(self, namespace: str, label: str, label_value: str = None) -> List[V1ConfigMap]:
        config_maps = self.v1.list_namespaced_config_map(namespace=namespace, label_selector=label)
        return self.filter_resources(config_maps, label_value)

    def find_secrets(self, namespace: str, label: str, label_value: str = None) -> List[V1Secret]:
        secrets = self.v1.list_namespaced_secret(namespace=namespace, label_selector=label)
        return self.filter_resources(secrets, label_value)

    @staticmethod
    def extract_yaml_strings_from_resources(resources: List[Union[V1ConfigMap, V1Secret]]) -> List[str]:
        yaml_str = list()
        for res in resources:
            for k in filter(lambda key: re.search("\\.yml|\\.yaml$", key), res.data.keys()):
                if type(res) == V1Secret:
                    Logger.debug(f"Found yaml in key '{k}' for secret '{res.metadata.name}'")
                    yaml_str.append(base64.b64decode(res.data[k]).decode())
                else:
                    Logger.debug(f"Found yaml in key '{k}' for configmap '{res.metadata.name}'")
                    yaml_str.append(res.data[k])

        return yaml_str

    def any_resource_has_changed(self, resources: List[Union[V1ConfigMap, V1Secret]]) -> bool:
        has_changed = False
        if len(self.watch_list) == 0:
            has_changed = True
            for res in resources:
                self.watch_resource(res)
        else:
            for res in resources:
                r_name = res.metadata.name
                r_type = ResourceType.SECRET if type(res) == V1Secret else ResourceType.CONFIGMAP
                watched_resource = next(filter(lambda r: r_name == r.name and r_type == r.type, self.watch_list), None)
                if watched_resource is None:
                    self.watch_resource(res)
                    has_changed = True
                    break
                elif watched_resource.version != res.metadata.resource_version:
                    watched_resource.version = res.metadata.resource_version
                    has_changed = True
                    break
        return has_changed

    def watch_resource(self, resource: Union[V1ConfigMap, V1Secret]):
        self.watch_list.append(
            WatchedResource(
                name=resource.metadata.name,
                version=resource.metadata.resource_version,
                type=ResourceType.SECRET if type(resource) == V1Secret else ResourceType.CONFIGMAP
            ))
