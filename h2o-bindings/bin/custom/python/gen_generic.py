extensions = dict(
    __init__validation="""
if all(kwargs.get(name, None) is None for name in ["model_key", "path"]):
    raise H2OValueError('At least one of ["model_key", "path"] is required.')
""",
    __class__="""
def _requires_training_frame(self):
    \"""
    Determines if Generic model requires a training frame.
    :return: False.
    \"""
    return False

@staticmethod
def from_file(file=str):
    \"""
    Creates new Generic model by loading existing embedded model into library, e.g. from H2O MOJO.
    The imported model must be supported by H2O.
    :param file: A string containing path to the file to create the model from
    :return: H2OGenericEstimator instance representing the generic model
    \"""
    model = H2OGenericEstimator(path = file)
    model.train()
    
    return model
"""
)
