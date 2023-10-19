from lum import odinson  # import Document as odinson.Document
from .utils import TEST_DOC_PATH
import json
import os
import unittest
import pytest


# see https://docs.python.org/3/library/unittest.html#basic-example
class TestOdinsonDocument(unittest.TestCase):
    def test_load_from(self):
        """odinson.Document.from_file() should load an odinson.Document from a path to a Odinson Document JSON file."""
        od = odinson.Document.from_file(TEST_DOC_PATH)
        self.assertTrue(
            isinstance(od, odinson.Document), f"{type(od)} was not an odinson.Document"
        )
