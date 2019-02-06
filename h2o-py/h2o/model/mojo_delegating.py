import h2o

class MojoDelegatingModel:
    """
    A model backed by a MOJO
    """

    def __init__(self, file_name):
        mojo_key  = h2o.lazy_import(file_name)[0]
        
        params = {"mojo_file_key": mojo_key}
        self._response = h2o.api(endpoint="POST /3/MojoDelegatingModel", data=params) 
        
    @property
    def response(self):
        return self._response
