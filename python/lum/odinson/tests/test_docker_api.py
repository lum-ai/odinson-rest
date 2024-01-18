from lum import odinson  # import Document as odinson.Document
from lum.odinson.rest.docker import DockerBasedOdinsonAPI
from .utils import TEST_DOC_PATH
import json
import os
import unittest
import pytest
import tempfile
import requests
import time

# def create_temp_dir():
#   # copy doc file to docs/
#   pass


# see https://docs.python.org/3/library/unittest.html#basic-example
class TestDockerAPI(unittest.TestCase):
    def setUp(self):
        """special method called before each test.
        See https://docs.python.org/3/library/unittest.html
        """
        self.test_doc = odinson.Document.from_file(TEST_DOC_PATH)
        self.indexdir = tempfile.TemporaryDirectory()
        self.engine = DockerBasedOdinsonAPI(
            local_path=self.indexdir.name, keep_alive=False
        )
        MAX_WAIT = 5
        STEP = 0.5
        ELAPSED = 0
        while ELAPSED < MAX_WAIT:
            try:
                len(self.engine)
            except:
                pass
            time.sleep(STEP)
            ELAPSED += STEP

    def tearDown(self):
        """special method called after each test.
        See https://docs.python.org/3/library/unittest.html
        """
        # NOTE: we must stop the container before destroying the mounted volume
        # close API
        self.engine.close()
        # clean up test index
        self.indexdir.cleanup()

    def test_index_doc(self):
        """engine.index(doc) should store an odinson.Document in the index."""
        self.engine.index(self.test_doc, max_tokens=-1)
        # print(f"Num. docs:\t{len(self.engine)}")
        actual = len(self.engine)
        self.assertTrue(
            actual > 0,
            f"index {self.indexdir.name} did not contain any docs after indexing.  Instead found {actual}",
        )

    def test_update_doc(self):
        """engine.update(doc) should replace an odinson.Document in the index."""
        self.engine.index(self.test_doc, max_tokens=-1)
        # print(f"Num. docs:\t{len(self.engine)}")
        doc2 = self.test_doc.copy(metadata=[])
        res = self.engine.update(doc2, max_tokens=-1)
        self.assertTrue(
            res == True, f"engine.update() should replace doc with the same ID"
        )

    def test_delete_doc(self):
        """engine.delete(doc) should remove an odinson.Document in the index."""
        self.engine.index(self.test_doc, max_tokens=-1)
        self.engine.delete(self.test_doc)
        actual = len(self.engine)
        self.assertTrue(
            actual == 0,
            f"index {self.indexdir.name} should not contain any docs after indexing and deleting the same doc.  Instead found {actual}",
        )
