try:
    from .info import info
    from .doc import Document

    __version__ = info.version
except:
    print("Failed to import info")
