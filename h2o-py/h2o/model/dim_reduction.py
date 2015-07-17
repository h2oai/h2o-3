"""
DimReduction Models
"""

from metrics_base import *

class H2ODimReductionModel(ModelBase):

    def __init__(self, dest_key, model_json):
        super(H2ODimReductionModel, self).__init__(dest_key, model_json,H2ODimReductionModelMetrics)

    def screeplot(self, type="barplot", **kwargs):
        """
        Produce the scree plot
        :param type: type of plot. "barplot" and "lines" currently supported
        :param show: if False, the plot is not shown. matplotlib show method is blocking.
        :return: None
        """
        # check for matplotlib. exit if absent.
        try:
            imp.find_module('matplotlib')
            import matplotlib
            if 'server' in kwargs.keys() and kwargs['server']: matplotlib.use('Agg', warn=False)
            import matplotlib.pyplot as plt
        except ImportError:
            print "matplotlib is required for this function!"
            return

        variances = [s**2 for s in self._model_json['output']['importance'].cell_values[0][1:]]
        plt.xlabel('Components')
        plt.ylabel('Variances')
        plt.title('Scree Plot')
        plt.xticks(range(1,len(variances)+1))
        if type == "barplot": plt.bar(range(1,len(variances)+1), variances)
        elif type == "lines": plt.plot(range(1,len(variances)+1), variances, 'b--')
        if not ('server' in kwargs.keys() and kwargs['server']): plt.show()
