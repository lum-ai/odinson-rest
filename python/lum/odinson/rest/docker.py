from lum.odinson.rest.api import OdinsonBaseAPI
from contextlib import closing
from typing import Optional
import socket
import docker

__all__ = ["DockerBasedOdinsonAPI"]
class DockerBasedOdinsonAPI(OdinsonBaseAPI):
    DEFAULT_IMAGE: str = "lumai/odinson-rest-api:experimental"
    def __init__(self, local_path: str, image_name: str = DEFAULT_IMAGE, local_port: Optional[int] = None, keep_alive: bool = False):
      self.client = docker.from_env()
      self.local_path = local_path
      self.image_name = image_name
      self.local_port = local_port or DockerBasedOdinsonAPI.get_unused_port()
      self.keep_alive = keep_alive
      self.container = self.client.containers.run(
        self.image_name, 
        name="odinson", 
        # strangely: container -> host
        ports={9000:self.local_port}, 
        auto_remove=True,
        detach=True,
        volumes={self.local_path: {"bind": "/app/data", "mode": "rw"}}
      )
      super().__init__(address=f"http://127.0.0.1:{self.local_port}")
    
    @staticmethod
    def get_unused_port() -> int:
        """Selects an unbound port.
        See https://stackoverflow.com/a/45690594/1318989
        """
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as s:
            s.bind(('', 0))
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            return s.getsockname()[1]
  
    @property
    def container_id(self) -> str:
       """Retrieves container ID for odinson REST API service"""
       return self.container.id
    
    def is_running(self):
      """Checks if docker container for odinson REST API service is running"""
      container_id = self.container_id
      try:
        self.client.containers.get(container_id)
        return True
      except Exception as e:
         return False
            
    def _close(self) -> bool:
      """Terminates docker container for odinson REST API service"""
      container_id = self.container_id
      if self.is_running():
        try:
            #print(f"Killing docker container {container_id} for Odinson REST API")
            self.container.kill()
            #self.container.remove()
            return True
        except Exception as e:
            print(f"Failed to kill {container_id}")
            print(e)
            return False

    def __del__(self):
      if not self.keep_alive and self.is_running():
          self._close() 