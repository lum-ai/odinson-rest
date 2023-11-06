from lum import odinson  # import Document as odinson.Document
from .utils import TEST_DOC_PATH
import json
import os
import unittest
import pytest


# see https://docs.python.org/3/library/unittest.html#basic-example
class TestOdinsonSentence(unittest.TestCase):
    def test_property_access(self):
        """odinson.Sentence should store token attributes for easy access via sent.attribute_name.
        """
        od = odinson.Document.from_file(TEST_DOC_PATH)
        s = od.sentences[0]
        self.assertTrue(
            len(s.lemma) > 0, f"s.lemma should not be empty, but returned {s.lemma}"
        )

    def test_copy_empty_fields(self):
        """odinson.Sentence.copy() should fail if fields are empty."""
        od = odinson.Document.from_file(TEST_DOC_PATH)
        s = od.sentences[0]
        self.assertRaises(Exception, s.copy, fields=[])