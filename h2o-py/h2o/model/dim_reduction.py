# -*- encoding: utf-8 -*-
"""
Dimension reduction model.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from .model_base import ModelBase
from .metrics_base import *
import h2o


class H2ODimReductionModel(ModelBase):
    def num_iterations(self):
        """Get the number of iterations that it took to converge or reach max iterations.

        Returns
        -------
          Number of iterations (integer)
        """
        o = self._model_json["output"]
        return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('number_of_iterations')]

    def objective(self):
        """Get the final value of the objective function from the GLRM model.

        Returns
        -------
          Final objective value
        """
        o = self._model_json["output"]
        return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('final_objective_value')]

    def final_step(self):
        """Get the final step size from the GLRM model.

        Returns
        -------
          Final step size
        """
        o = self._model_json["output"]
        return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('final_step_size')]

    def archetypes(self):
        """

        Returns
        -------
          The archetypes (Y) of the GLRM model.
        """
        o = self._model_json["output"]
        yvals = o["archetypes"].cell_values
        archetypes = []
        for yidx, yval in enumerate(yvals):
            archetypes.append(list(yvals[yidx])[1:])
        return archetypes

    def reconstruct(self, test_data, reverse_transform=False):
        """Reconstruct the training data from the GLRM model and impute all missing
        values.

        Parameters
        ----------
          test_data : H2OFrame
            The dataset upon which the H2O GLRM model was trained.

          reverse_transform : logical
            Whether the transformation of the training data during model-building should
            be reversed on the reconstructed frame.

        Returns
        -------
          Return the approximate reconstruction of the training data.
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"reconstruct_train": True, "reverse_transform": reverse_transform})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])

    def proj_archetypes(self, test_data, reverse_transform=False):
        """Convert archetypes of a GLRM model into original feature space.

        Parameters
        ----------
          test_data : H2OFrame
            The dataset upon which the H2O GLRM model was trained.

          reverse_transform : logical
            Whether the transformation of the training data during model-building should
            be reversed on the projected archetypes.

        Returns
        -------
          Return the GLRM archetypes projected back into the original training data's
          feature space.
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"project_archetypes": True, "reverse_transform": reverse_transform})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])

    def screeplot(self, type="barplot", **kwargs):
        """Produce the scree plot

        Parameters
        ----------
          type : str
           "barplot" and "lines" currently supported

          show: str
            if False, the plot is not shown. matplotlib show method is blocking.
        """
        # check for matplotlib. exit if absent.
        try:
            imp.find_module('matplotlib')
            import matplotlib
            if 'server' in list(kwargs.keys()) and kwargs['server']: matplotlib.use('Agg', warn=False)
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
        if not ('server' in list(kwargs.keys()) and kwargs['server']): plt.show()
