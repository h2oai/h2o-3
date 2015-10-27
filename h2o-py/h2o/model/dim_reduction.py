from model_base import ModelBase
from metrics_base import *


class H2ODimReductionModel(ModelBase):

    def num_iterations(self):
      """
      Get the number of iterations that it took to converge or reach max iterations.

      :return: number of iterations (integer)
      """
      o = self._model_json["output"]
      return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('number_of_iterations')]
    
    def objective(self):
      """
      Get the final value of the objective function from the GLRM model.

      :return: final objective value (double)
      """
      o = self._model_json["output"]
      return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('final_objective_value')]
   
    def final_step(self):
      """
      Get the final step size from the GLRM model.

      :return: final step size (double)
      """
      o = self._model_json["output"]
      return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('final_step_size')]
    
    def archetypes(self):
      """
      :return: the archetypes (Y) of the GLRM model.
      """
      o = self._model_json["output"]
      yvals = o["archetypes"].cell_values
      archetypes = []
      for yidx, yval in enumerate(yvals):
        archetypes.append(list(yvals[yidx])[1:])
      return archetypes
    
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