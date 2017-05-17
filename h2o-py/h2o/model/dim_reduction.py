# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.shared_utils import can_use_pandas
from h2o.utils.compatibility import *  # NOQA

from .model_base import ModelBase
from .metrics_base import *  # NOQA
import h2o


class H2ODimReductionModel(ModelBase):
    """
    Dimension reduction model, such as PCA or GLRM.
    """


    def varimp(self, use_pandas=False):
        """
        Return the Importance of components associcated with a pca model.

        use_pandas: ``bool``  (default: ``False``).
        """
        model = self._model_json["output"]
        if "importance" in list(model.keys()) and model["importance"]:
            vals = model["importance"].cell_values
            header = model["importance"].col_header
            if use_pandas and can_use_pandas():
                import pandas
                return pandas.DataFrame(vals, columns=header)
            else:
                return vals
        else:
            print("Warning: This model doesn't have importances of components.")

    def num_iterations(self):
        """Get the number of iterations that it took to converge or reach max iterations."""
        o = self._model_json["output"]
        return o["model_summary"]["number_of_iterations"][0]


    def objective(self):
        """Get the final value of the objective function."""
        o = self._model_json["output"]
        return o["model_summary"]["final_objective_value"][0]


    def final_step(self):
        """Get the final step size for the model."""
        o = self._model_json["output"]
        return o["model_summary"]["final_step_size"][0]


    def archetypes(self):
        """The archetypes (Y) of the GLRM model."""
        o = self._model_json["output"]
        yvals = o["archetypes"].cell_values
        archetypes = []
        for yidx, yval in enumerate(yvals):
            archetypes.append(list(yvals[yidx])[1:])
        return archetypes


    def reconstruct(self, test_data, reverse_transform=False):
        """
        Reconstruct the training data from the model and impute all missing values.

        :param H2OFrame test_data: The dataset upon which the model was trained.
        :param bool reverse_transform: Whether the transformation of the training data during model-building
            should be reversed on the reconstructed frame.

        :returns: the approximate reconstruction of the training data.
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"reconstruct_train": True, "reverse_transform": reverse_transform})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])


    def proj_archetypes(self, test_data, reverse_transform=False):
        """
        Convert archetypes of the model into original feature space.

        :param H2OFrame test_data: The dataset upon which the model was trained.
        :param bool reverse_transform: Whether the transformation of the training data during model-building
            should be reversed on the projected archetypes.

        :returns: model archetypes projected back into the original training data's feature space.
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"project_archetypes": True, "reverse_transform": reverse_transform})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])


    def screeplot(self, type="barplot", **kwargs):
        """
        Produce the scree plot.

        Library ``matplotlib`` is required for this function.

        :param str type: either ``"barplot"`` or ``"lines"``.
        """
        # check for matplotlib. exit if absent.
        is_server = kwargs.pop("server")
        if kwargs:
            raise ValueError("Unknown arguments %s to screeplot()" % ", ".join(kwargs.keys()))
        try:
            import matplotlib
            if is_server: matplotlib.use('Agg', warn=False)
            import matplotlib.pyplot as plt
        except ImportError:
            print("matplotlib is required for this function!")
            return

        variances = [s ** 2 for s in self._model_json['output']['importance'].cell_values[0][1:]]
        plt.xlabel('Components')
        plt.ylabel('Variances')
        plt.title('Scree Plot')
        plt.xticks(list(range(1, len(variances) + 1)))
        if type == "barplot":
            plt.bar(list(range(1, len(variances) + 1)), variances)
        elif type == "lines":
            plt.plot(list(range(1, len(variances) + 1)), variances, 'b--')
        if not is_server: plt.show()
