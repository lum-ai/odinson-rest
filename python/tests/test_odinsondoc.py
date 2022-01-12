from lum import odinson  # import Document as odinson.Document
import json
import os
import unittest

# see https://docs.python.org/3/library/unittest.html#basic-example
class TestOdinsonDocument(unittest.TestCase):
    doc_path = os.path.join(
        os.path.dirname(os.path.realpath(__file__)), "data", "odinson-doc.json"
    )
    with open(doc_path, "r") as infile:
        odinson_json = json.load(infile)

    def test_load_from(self):
        """odinson.Document.parse_file() should load an odinson.Document from a path to a Odinson Document JSON file."""
        od = odinson.Document.parse_file(TestOdinsonDocument.doc_path)
        self.assertTrue(
            isinstance(od, odinson.Document), f"{type(od)} was not an odinson.Document"
        )
