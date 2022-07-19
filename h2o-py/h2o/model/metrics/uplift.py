from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase
from h2o.plot import get_matplotlib_pyplot


class H2OBinomialUpliftModelMetrics(MetricsBase):
    """
    This class is available only for Uplift DRF model.
    This class is essentially an API for the AUUC object.
    """
    
    def _str_items_custom(self):
        items = [
            "AUUC: {}".format(self.auuc()),
            "AUUC normalized: {}".format(self.auuc_normalized()),
        ]
        auuct = self.auuc_table()
        if auuct: items.append(auuct)
        items.append("Qini value: {}".format(self.qini()))
        aecut = self.aecu_table()
        if aecut: items.append(aecut)
        return items
    
    def auuc(self, metric=None):
        """
        Retrieve area under cumulative uplift curve (AUUC) value.
        
        :param metric: AUUC metric type. One of:

            - "None" (default; takes default metric from model parameters)
            - "AUTO" (defaults to "qini")
            - "qini"
            - "lift"
            - "gain"

        :returns: AUUC value.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.auuc()
        """
        if metric is None:
            return self._metric_json['AUUC']
        else:
            assert metric in ['AUTO', 'qini', 'lift', 'gain'],\
                "AUUC metric "+metric+" should be 'AUTO', 'qini','lift' or 'gain'."
            if metric == "AUTO": metric = 'qini'
            return self._metric_json['auuc_table'][metric][0]

    def auuc_normalized(self, metric=None):
        """
        Retrieve normalized area under cumulative uplift curve (AUUC) value.
        
        :param metric: AUUC metric type. One of:
        
            - "None" (default; takes default metric from model parameters)
            - "AUTO" (defaults to "qini")
            - "qini"
            - "lift"
            - "gain"

        :returns: normalized AUUC value.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.auuc_normalized()
        """
        if metric is None:
            return self._metric_json['auuc_normalized']
        else:
            assert metric in ['AUTO', 'qini', 'lift', 'gain'], \
                "AUUC metric "+metric+" should be 'AUTO', 'qini','lift' or 'gain'."
            if metric == "AUTO": metric = 'qini'
            return self._metric_json['auuc_table'][metric][1]

    def qini(self):
        """
        Retrieve Qini value (area between Qini cumulative uplift curve and random curve).
        
        :returns: Qini value.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.qini()
        """
        return self._metric_json['qini']

    def aecu(self, metric="AUTO"):
        """
        Retrieve AECU value (average excess cumulative uplift - area between Uplift curve and random curve).
        
        :param metric: AECU metric type One of:

            - "None"
            - "qini"
            - "lift"
            - "gain"
            - "AUTO" (default; defaults to "qini")
            
        :returns: AECU value.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.aecu()
        """
        assert metric in ['AUTO', 'qini', 'lift', 'gain'], \
            "AECU metric "+metric+" should be 'qini','lift' or 'gain'."
        if metric == 'AUTO': metric = 'qini'
        return self._metric_json['aecu_table'][metric][0]
            
    def uplift(self, metric="AUTO"):
        """
        Retrieve uplift values for each bin. 
        
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "AUTO" (default; defaults to "qini") 
        
        :returns: a list of uplift values.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.uplift()
        """
        assert metric in ['AUTO', 'qini', 'lift', 'gain']
        if metric == "AUTO": 
            metric = 'qini'
        return self._metric_json["thresholds_and_metric_scores"][metric]
    
    def uplift_normalized(self, metric="AUTO"):
        """
        Retrieve normalized uplift values for each bin. 
        
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "AUTO" (default; defaults to "qini") 
        
        :returns: a list of normalized uplift values.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.uplift_normalized()
        """
        assert metric in ['AUTO', 'qini', 'lift', 'gain']
        if metric == "AUTO": 
            metric = 'qini'
        return self._metric_json["thresholds_and_metric_scores"][metric+"_normalized"]

    def uplift_random(self, metric="AUTO"):
        """
        Retrieve random uplift values for each bin. 
        
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "AUTO" (default; defaults to "qini") 
        
        :returns: a list of random uplift values.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.uplift_random()
        """
        assert metric in ['AUTO', 'qini', 'lift', 'gain']
        if metric == "AUTO": 
            metric = 'qini'
        return self._metric_json["thresholds_and_metric_scores"][metric+"_random"]    

    def n(self):
        """
        Retrieve cumulative sum of numbers of observations in each bin. 
        
        :returns: a list of numbers of observation.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.n()
        """  
        return self._metric_json["thresholds_and_metric_scores"]["n"]
    
    def thresholds(self):
        """
        Retrieve prediction thresholds for each bin. 
        
        :returns: a list of thresholds.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.thresholds()
        """
        return self._metric_json["thresholds_and_metric_scores"]["thresholds"]

    def thresholds_and_metric_scores(self):
        """
        Retrieve thresholds and metric scores table.
        
        :returns: a thresholds and metric scores table for the specified key(s).
        
        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.thresholds_and_metric_scores()
        """
        return self._metric_json["thresholds_and_metric_scores"]

    def auuc_table(self):
        """
        Retrieve all types of AUUC in a table.
         
        :returns: a table of AUUCs.
    
        :examples:
         
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.auuc_table()
        """
        return self._metric_json["auuc_table"]

    def aecu_table(self):
        """
        Retrieve all types of AECU values in a table.
         
        :returns: a table of AECU values.
    
        :examples:
         
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.aecu_table()
        """
        return self._metric_json["aecu_table"]

    def plot_uplift(self, server=False, save_to_file=None, plot=True, metric="AUTO", normalize=False):
        """
        Plot Uplift Curve. 
        
        :param server: if ``True``, generate plot inline using matplotlib's Anti-Grain Geometry (AGG) backend.
        :param save_to_file: filename to save the plot to.
        :param plot: ``True`` to plot curve, ``False`` to get a tuple of values at axis x and y of the plot 
            (number of observations and uplift values)
        :param metric: AUUC metric type. One of:

            - "qini"
            - "lift"
            - "gain"
            - "AUTO" (default; defaults to "qini")
            
        :param normalize: If ``True``, normalized values are plotted.

        :examples:
        
        >>> from h2o.estimators import H2OUpliftRandomForestEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
        >>> treatment_column = "treatment"
        >>> response_column = "conversion"
        >>> train[treatment_column] = train[treatment_column].asfactor()
        >>> train[response_column] = train[response_column].asfactor()
        >>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6"]
        >>>
        >>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, 
        ...                                               max_depth=5,
        ...                                               treatment_column=treatment_column,
        ...                                               uplift_metric="kl",
        ...                                               distribution="bernoulli",
        ...                                               min_rows=10,
        ...                                               auuc_type="gain")
        >>> uplift_model.train(y=response_column, x=predictors, training_frame=train)
        >>> perf = uplift_model.model_performance()
        >>> perf.plot_uplift(plot=True)
        >>> n, uplift = perf.plot_uplift(plot=False)
        """
        assert metric in ['AUTO', 'qini', 'lift', 'gain'], \
            "Metric "+metric+" should be 'AUTO', 'qini','lift' or 'gain'."
        if plot:
            plt = get_matplotlib_pyplot(server)
            if plt is None:
                return
            plt.ylabel('Cumulative '+metric)
            plt.xlabel('Number Targeted')
            rnd = self.uplift_random(metric)
            if normalize:
                plt.title('Cumulate Uplift Curve - '+metric+"\n"+r'Normalized AUUC={0:.4f}'.format(self.auuc_normalized(metric)))
                uplift = self.uplift_normalized(metric)
                if metric != "lift":
                    max = abs(rnd[len(rnd)-1])
                    rnd = [x / max for x in rnd]
            else:
                plt.title('Cumulate Uplift Curve - '+metric+"\n"+r'AUUC={0:.4f}'.format(self.auuc(metric)))
                uplift = self.uplift(metric)
            n = self.n()
            plt.plot(n, uplift, 'b-', label='uplift')
            plt.plot(n, rnd, 'k--', label='random')
            if metric == "lift":
                plt.legend(loc='upper right')
            else:
                plt.legend(loc='lower right')
            plt.grid(True)
            plt.tight_layout()
            if not server:
                plt.show()
            if save_to_file is not None:  # only save when a figure is actually plotted
                plt.savefig(save_to_file)
        else:
            return self.n(), self.uplift(metric)
