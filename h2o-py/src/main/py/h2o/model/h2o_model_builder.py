"""
An abstract model builder.
"""

import abc
from . import H2OFrame
from . import h2oConn


class H2OModelBuilder(object):
    """Abstract base class for H2O Model Builder objects

    Every model depends on a builder.
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, parameters=None, algo="", training_frame=None):
        self.parameters = parameters
        self.algo = algo
        self.training_frame = training_frame
        self.fitted_model = None

    def fit(self, x=None, y=None, validation_frame=None):
        """
        Fit the model to the inputs x, y
        :param x: A list[] of 0-based indices or column names.
        :param y: A 0-based index or a column name
        :return: Returns self (a fitted model).
        """

        if not self.training_frame:
            raise ValueError("No training frame supplied.")

        if not x or not y:
            if not self.parameters["x"] or not self.parameters["y"]:
                raise ValueError("No fit can be made, missing training data (x,y).")
            if x:
                self.parameters["x"] = x
            if y:
                self.parameters["y"] = y

        x = self.parameters["x"]
        y = self.parameters["y"]
        dataset = self.training_frame

        if not isinstance(dataset, H2OFrame):
            raise ValueError("`dataset` must be a H2OFrame. Got: " + str(type(dataset)))

        if not self.training_frame[x]:
            raise ValueError(x + " must be column(s) in " + str(dataset))

        # swap out column indices (0-based) for column names
        if isinstance(x[0], int): x = [dataset.names()[i] for i in x]

        if isinstance(y, int): y = dataset.names()[y]

        self.parameters["ignored_columns"] = \
            [i for i in dataset.names() if i not in x and i != y]

        self.parameters["response"] = y

        # Since H2O Frames are a collection of H2OVecs, there is no "frame_key"
        # The method `send_frame` cbinds the frame together and gives it a key.
        dataset_key = H2OFrame.send_frame(dataset)
        self.parameters["training_frame"] = dataset_key

        # Do the same for the validation frame if there is one
        validation_passed_to_fit = False
        if not validation_frame:
            validation_frame = self.parameters["validation"]
        else:
            validation_passed_to_fit = True
        if validation_frame:
            message = "Validation passed to " + \
                      ("fit" if validation_passed_to_fit else "model builder")
            if not isinstance(validation_frame, H2OFrame):
                raise ValueError(message + " must be of type H2OFrame. "
                                 "Got: " + str(type(validation_frame)))
            validation_key = H2OFrame.send_frame(validation_frame)
            self.parameters["validation"] = validation_key

        builders_response = h2oConn.do_safe_get(url_suffix="ModelBuilders/" + self.algo)
        model_params = builders_response["model_builders"][self.algo]["parameters"]

        # take values from self.params if they map to model_params
        model_params.update({k: v for k, v in self.parameters if k in model_params})
