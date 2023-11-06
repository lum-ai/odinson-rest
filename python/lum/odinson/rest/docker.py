from lum.odinson.rest.api import OdinsonBaseAPI
from contextlib import closing
from typing import List, Optional
import socket
import docker
import tempfile
import shutil
import uuid
import time

__all__ = ["DockerBasedOdinsonAPI"]


class DockerBasedOdinsonAPI(OdinsonBaseAPI):
    
    DEFAULT_TOKEN_ATTRIBUTES = [
      "raw", "word", "norm", "lemma", "tag", "chunk", "entity", "incoming", "outgoing"
    ]

    DEFAULT_IMAGE: str = "lumai/odinson-rest-api:latest"
    ODINSON_INTERNAL_PORT: int = 9000
    ODINSON_INTERNAL_DATA_PATH: str = "/app/data"

    def __init__(
        self,
        local_path: Optional[str] = None,
        image_name: str = DEFAULT_IMAGE,
        container_name: Optional[str] = f"odinson-{uuid.uuid4()}",
        local_port: Optional[int] = None,
        keep_alive: bool = False,
        max_mem_gb: int = 2,
        file_encoding: str = "UTF-8",
        token_attributes: Optional[List[str]] = None
    ):
        self.client = docker.from_env()
        self.temp_dir = tempfile.mkdtemp()
        self.local_path: Optional[str] = local_path or self.temp_dir
        self.max_mem_gb: int = max_mem_gb
        self.file_encoding: str = file_encoding
        self.token_attributes: List[str] = token_attributes or DockerBasedOdinsonAPI.DEFAULT_TOKEN_ATTRIBUTES
        self.image_name: str = image_name
        self.local_port: int = local_port or DockerBasedOdinsonAPI.get_unused_port()
        self.keep_alive: bool = keep_alive
        self.container_name: str = container_name
        # if we're connecting to an existing service,
        # we need to alter some of our attributes...
        if self.is_running():
            self.container = self.client.containers.get(self.container_name)
            self.keep_alive = True
            self.local_path: str = [entry.get("Source") for entry in self.container.attrs.get("Mounts", []) if entry.get("Destination", "???") == DockerBasedOdinsonAPI.ODINSON_INTERNAL_DATA_PATH][0]
            self.image_name: str = self.container.image.tags[0]
            self.local_port: int = self.client.api.port(self.container_name, DockerBasedOdinsonAPI.ODINSON_INTERNAL_PORT)
        else:
            self.container = self.client.containers.run(
                self.image_name,
                name=self.container_name,
                # NOTE: strangely: container -> host
                ports={DockerBasedOdinsonAPI.ODINSON_INTERNAL_PORT: self.local_port},
                auto_remove=True,
                detach=True,
                volumes={self.local_path: {"bind": DockerBasedOdinsonAPI.ODINSON_INTERNAL_DATA_PATH, "mode": "rw"}},
                environment={
                  #-Dodinson.compiler.allTokenFields=["a", "b", "c"]
                  "_JAVA_OPTIONS": f"-Xmx{self.max_mem_gb}g -Dplay.server.pidfile.path=/dev/null -Dfile.encoding={self.file_encoding}",
                  "ODINSON_TOKEN_ATTRIBUTES": ",".join(self.token_attributes)
                }
            )
        super().__init__(address=f"http://127.0.0.1:{self.local_port}")
  
    # def __enter__(self):
    #     return self
    
    # def __exit__(self, exception_type, exception_value, exception_traceback):
    #     # Exception handling here
    #     # close index
    #     # FIXME: using this produces a Connection Reset by Peer error
    #     self.close()

    @staticmethod
    def using_container(container_name: str) -> "DockerBasedOdinsonAPI":
        """Connect to an existing containerized Odinson REST API service"""
        return DockerBasedOdinsonAPI(container_name=container_name)

    @staticmethod
    def get_unused_port() -> int:
        """Selects an unbound port.
        See https://stackoverflow.com/a/45690594/1318989
        """
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as s:
            s.bind(("", 0))
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            return s.getsockname()[1]

    def is_running(self):
        """Checks if docker container for odinson REST API service is running"""
        container_name = self.container_name
        try:
            self.client.containers.get(container_name)
            return True
        except Exception as e:
            return False

    def close(self) -> bool:
        """Terminates docker container for odinson REST API service"""
        if self.is_running():
            try:
                # print(f"Killing docker container {container_id} for Odinson REST API")
                self.container.kill()
                # self.container.remove()
                shutil.rmtree(self.temp_dir, ignore_errors=True)
                return True
            except Exception as e:
                print(f"Failed to kill {self.container_name}")
                #print(e)
                return False

    def __del__(self):
        if self.is_running() and not self.keep_alive:
            self.close()
