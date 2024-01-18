from lum import odinson  # import Document as odinson.Document
from .utils import TEST_DOC_PATH
import json
import os
import unittest
import pytest


# see https://docs.python.org/3/library/unittest.html#basic-example
class TestOdinsonDocument(unittest.TestCase):
    def test_load_from(self):
        """odinson.Document.from_file() should load an odinson.Document from a path to an Odinson Document JSON file."""
        od = odinson.Document.from_file(TEST_DOC_PATH)
        self.assertTrue(
            isinstance(od, odinson.Document), f"{type(od)} was not an odinson.Document"
        )

    def test_property_access(self):
        """odinson.Document should store token attributes for easy access via doc.attribute_name.
        """
        od = odinson.Document.from_file(TEST_DOC_PATH)
        self.assertTrue(
            len(od.lemma) > 0, f"doc.lemma should not be empty, but returned {od.lemma}"
        )
    
    def test_copy(self):
        """odinson.Document.copy() should produce copies with only the specified changes."""
        _od = odinson.Document.from_file(TEST_DOC_PATH)
        od = _od.copy(id="blarg")
        self.assertTrue(
            _od.id != od.id, f"Doc ID of original and clone should differ, but clone's ID was {od.id}"
        )
        self.assertTrue(
            all(_od.sentences[i] == od.sentences[i] for i in range(len(_od.sentences))), f"Sentences of original and clone differing only in Doc ID should be identical"
        )