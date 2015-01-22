"""
An abstract model builder.
"""

import abc
from . import H2OFrame
from . import h2oConn
from . import ModelBase
from . import H2OJob
import h2o


class H2OMissingFrameException(Exception):
    pass


class H2OModelBuilder(ModelBase):
    """Abstract base class for H2O Model Builder objects

    Every model depends on a builder.
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, parameters=None, algo="", training_frame=None):
        super(H2OModelBuilder, self).__init__(parameters, algo)
        self.training_frame = training_frame

    def fit(self, x=None, y=None, validation_frame=None):
        """
        Fit the model to the inputs x, y
        :param x: A list[] of 0-based indices or column names.
        :param y: A 0-based index or a column name
        :return: Returns self (a fitted model).
        """

        # Update self.parameters with any changes to the member vars
        self._update()

        # check that x and y are not both None and update self._parameters
        x, y = self._set_and_check_x_y(x, y)

        # check that the training_frame is a H2OFrame, and check that the x's are valid
        dataset = self._check_training_frame(x)

        # swap out column indices (0-based) for column names
        x, y = H2OModelBuilder._indexed_columns_to_named_columns(x, y, dataset)

        # set the ignored_columns parameter
        self._set_ignored_columns(x, y, dataset)

        # set the ignored_columns list into self._parameters
        self._set_response_column(y)

        # cbind the H2OVecs and create a tmp key and put this key into self._parameters
        self._set_training_frame(dataset)

        # set the validation frame (similar to _set_training_frame)
        self._set_validation_frame(validation_frame)

        # fold default parameters from H2O and user-specified parameters together
        model_params = self._fold_default_params_with_user_params()

        # launch the job and poll
        url_suffix = "ModelBuilders/" + self._algo
        j = H2OJob(h2oConn.do_safe_post_json(url_suffix=url_suffix, params=model_params))\
            .poll()
        # set the fitted_model and model_type fields
        self._set_fitted_model_and_model_type(j.destination_key)

        # do some cleanup
        h2o.remove(self._parameters["training_frame"])
        if self._parameters["validation_frame"] is not None:
            h2o.remove(self._parameters["validation_frame"])

        # flowing return
        return self

    def show(self):
        pass

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass

    def summary(self):
        pass

    def _update(self):
        o = self
        a = [n for n in dir(o) if not callable(getattr(o, n)) and not n.startswith("_")]
        self._parameters = dict(zip(a, [getattr(o, i) for i in a]))

    def _set_and_check_x_y(self, x, y):
        if not x or not y:
            if not self._parameters["x"] or not self._parameters["y"]:
                raise ValueError("No fit can be made, missing training data (x,y).")
            if x:
                self._parameters["x"] = x
            if y:
                self._parameters["y"] = y
        return self._parameters["x"], self._parameters["y"]

    def _check_training_frame(self, x):
        if not self.training_frame:
            raise H2OMissingFrameException("No training frame supplied.")

        dataset = self.training_frame

        if not isinstance(dataset, H2OFrame):
            raise ValueError("`training_frame` must be a H2OFrame. Got: "
                             + str(type(dataset)))

        if not self.training_frame[x]:
            raise ValueError(x + " must be column(s) in " + str(dataset))

        return dataset

    @staticmethod
    def _indexed_columns_to_named_columns(x, y, dataset):
        if isinstance(x[0], int): x = [dataset.names()[i] for i in x]
        if isinstance(y, int): y = dataset.names()[y]
        return x, y

    def _set_ignored_columns(self, x, y, dataset):
        self._parameters["ignored_columns"] = \
            [i for i in dataset.names() if i not in x and i != y]

    def _set_response_column(self, y):
        self._parameters["response_column"] = y

    def _set_training_frame(self, dataset):
        # Since H2O Frames are a collection of H2OVecs, there is no "frame_key"
        # The method `send_frame` cbinds the frame together and gives it a key.
        dataset_key = H2OFrame.send_frame(dataset)
        self._parameters["training_frame"] = dataset_key

    # TODO: clean up this method and add comments
    def _set_validation_frame(self, validation_frame):
        validation_passed_to_fit = False
        if validation_frame:
            validation_frame = self._parameters["validation"]
            validation_passed_to_fit = True

        if validation_frame:
            message = "Validation passed to " + \
                      ("fit" if validation_passed_to_fit else "model builder")
            if not isinstance(validation_frame, H2OFrame):
                raise ValueError(message + " must be of type H2OFrame. "
                                           "Got: " + str(type(validation_frame)))
            validation_key = H2OFrame.send_frame(validation_frame)
            self._parameters["validation"] = validation_key

    def _fold_default_params_with_user_params(self):
        """
        Fold together the user parameters with the default parameters
        :return: A single dictionary of parameters
        """
        url_suffix = "ModelBuilders/" + self._algo

        # ask h2o what the default parameters are
        builders_response = h2oConn.do_safe_get_json(url_suffix=url_suffix)

        # fish out the parameters from the builders_response json object
        model_params_raw = builders_response["model_builders"][self._algo]["parameters"]

        # build a dictionary of the default parameters that self._algo expects
        model_params_defaults = {n["name"]: n["default_value"] for n in model_params_raw}

        # take values from self.params if they map to model_params
        parms_to_fill = [k for k in self._parameters.keys() if k in model_params_defaults]

        # fill in the default parameters with the user-specified values
        for k in parms_to_fill:
            model_params_defaults[k] = self._parameters[k]

        keys_to_pop = []
        for k in model_params_defaults:
            if model_params_defaults[k] is None:
                keys_to_pop += [k]

        if len(keys_to_pop) > 0:
            for k in keys_to_pop:
                model_params_defaults.pop(k, None)

        # return the default parameters folded together with the user-specified params
        return model_params_defaults

    def _set_fitted_model_and_model_type(self, destination_key):
        url_suffix = "Models/" + destination_key
        model = h2oConn.do_safe_get_json(url_suffix=url_suffix)["models"][0]
        self._fitted_model = model["output"]
        self._model_type = self._model_type.format(self._fitted_model["model_category"])