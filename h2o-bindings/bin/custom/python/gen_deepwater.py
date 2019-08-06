imports = """import h2o"""

class_doc = """
Build a Deep Learning model using multiple native GPU backends
Builds a deep neural network on an H2OFrame containing various data sources
"""

class_extras = """
@staticmethod
def available():
    \"""
    Ask the H2O server whether a Deep Water model can be built (depends on availability of native backends).
    :return: True if a deep water model can be built, or False otherwise.
    \"""
    builder_json = h2o.api("GET /3/ModelBuilders", data={"algo": "deepwater"})
    visibility = builder_json["model_builders"]["deepwater"]["visibility"]
    if visibility == "Experimental":
        print("Cannot build a Deep Water model - no backend found.")
        return False
    else:
        return True
"""
