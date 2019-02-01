import h2o

class MojoDelegatingModel:
    """
    A model backed by a MOJO
    """

    def __init__(self, file_name):
        params = {"mojo_file_path": file_name}
        self._response = h2o.api(endpoint="POST /3/MojoDelegatingModel", data=params)
        
    @property
    def response(self):
        return self._response
