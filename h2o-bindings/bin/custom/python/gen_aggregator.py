class_init_extra = """
self._parms["_rest_version"] = 99
"""

class_extras = """
@property
def aggregated_frame(self):
    if (self._model_json is not None and
        self._model_json.get("output", {}).get("output_frame", {}).get("name") is not None):
        out_frame_name = self._model_json["output"]["output_frame"]["name"]
        return H2OFrame.get_frame(out_frame_name)
"""

