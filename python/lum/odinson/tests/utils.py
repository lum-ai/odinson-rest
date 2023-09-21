import json
import os

# Test utilities

EXAMPLE = "Example"

TEST_DOC_PATH = os.path.join(
    os.path.dirname(os.path.realpath(__file__)), "data", "odinson-doc.json"
)
with open(TEST_DOC_PATH, "r") as infile:
    odinson_json = json.load(infile)