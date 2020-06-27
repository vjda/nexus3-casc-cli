class NexusCascError(Exception):
    """Custom exception"""

    def __init__(self, message: str, *args, **kwargs):
        self.message = message
        Exception.__init__(self, message, *args)


class NexusScriptApiError(NexusCascError):
    """Script api exception"""

    def __init__(self, message: str, path_url: str, status_code: int, *args, **kwargs):
        self.status_code = status_code
        self.path_url = path_url
        super().__init__(message, *args, **kwargs)
