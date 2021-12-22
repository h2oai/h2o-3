import h2o


class HStatistic:

    def _h(self, frame, variables):
        """
        Calculates Friedman and Popescu's H statistics, in order to test for the presence of an interaction between specified variables in h2o gbm and xgb models.
        H varies from 0 to 1. It will have a value of 0 if the model exhibits no interaction between specified variables and a correspondingly larger value for a 
        stronger interaction effect between them. NaN is returned if a computation is spoiled by weak main effects and rounding errors.
        
        See Jerome H. Friedman and Bogdan E. Popescu, 2008, "Predictive learning via rule ensembles", *Ann. Appl. Stat.*
        **2**:916-954, http://projecteuclid.org/download/pdfview_1/euclid.aoas/1223908046, s. 8.1.

        
        :param frame: the frame that current model has been fitted to
        :param variables: variables of the interest
        :return: H statistic of the variables 
        
        :examples:
        >>> prostate_train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate_train.csv")
        >>> prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
        >>> gbm_h2o = H2OGradientBoostingEstimator(ntrees=100, learn_rate=0.1,
        >>>                                 max_depth=5,
        >>>                                 min_rows=10,
        >>>                                 distribution="bernoulli")
        >>> gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
        >>> h = gbm_h2o.h(prostate_train, ['DPROS','DCAPS'])
        """
        kwargs = dict(
            model_id=self.model_id, 
            frame=frame.key, 
            variables=variables
        )
        json = h2o.api("POST /3/FriedmansPopescusH", data=kwargs)
        return json['h']
