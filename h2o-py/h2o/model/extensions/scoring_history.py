from h2o.exceptions import H2OValueError
from h2o.plot import decorate_plot_result, get_matplotlib_pyplot, RAISE_ON_FIGURE_ACCESS
from h2o.utils.shared_utils import can_use_pandas
from h2o.utils.typechecks import assert_is_type


class ScoringHistory:
    
    _default_metrics_ = dict(
        binomial='logloss',
        multinomial='classification_error',
        regression='rmse',
    )
    _allowed_metrics_ = dict(
        binomial=['logloss', 'auc', 'classification_error', 'rmse'],
        multinomial=['logloss', 'classification_error', 'rmse'],
        regression=['rmse', 'deviance', 'mae']
    )
    
    def _default_metric(self, type_):
        return self._default_metrics_.get(type_)
    
    def _allowed_metrics(self, type_):
        return self._allowed_metrics_.get(type_, [])
    
    def _get_scoring_history_to_plot(self):
        return self.scoring_history()
    
    def _validate_timestep(self, timestep):
        return timestep
    
    def scoring_history_plot(self, timestep, metric, server=False, save_plot_path=None):
        plt = get_matplotlib_pyplot(server)
        if plt is None: return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)
        
        scoring_history = self._get_scoring_history_to_plot()
        timestep = self._validate_timestep(timestep)
        training_metric = "training_{}".format(metric)
        validation_metric = "validation_{}".format(metric)
        if timestep == "duration":
            dur_colname = "duration_{}".format(scoring_history["duration"][1].split()[1])
            scoring_history[dur_colname] = [str(x).split()[0] for x in scoring_history["duration"]]
            timestep = dur_colname

        if can_use_pandas():
            valid = validation_metric in list(scoring_history)
            ylim = (scoring_history[[training_metric, validation_metric]].min().min(),
                    scoring_history[[training_metric, validation_metric]].max().max()) if valid \
                else (scoring_history[training_metric].min(), scoring_history[training_metric].max())
        else:
            valid = validation_metric in scoring_history.col_header
            ylim = (min(min(scoring_history[[training_metric, validation_metric]])),
                    max(max(scoring_history[[training_metric, validation_metric]]))) if valid \
                else (min(scoring_history[training_metric]), max(scoring_history[training_metric]))
        if ylim[0] == ylim[1]: ylim = (0, 1)

        fig = plt.figure()
        if valid:  # Training and validation scoring history
            plt.xlabel(timestep)
            plt.ylabel(metric)
            plt.title("Scoring History")
            plt.ylim(ylim)
            plt.plot(scoring_history[timestep], scoring_history[training_metric], label="Training")
            plt.plot(scoring_history[timestep], scoring_history[validation_metric], color="orange",
                     label="Validation")
            plt.legend()
        else:  # Training scoring history only
            plt.xlabel(timestep)
            plt.ylabel(training_metric)
            plt.title("Training Scoring History")
            plt.ylim(ylim)
            plt.plot(scoring_history[timestep], scoring_history[training_metric])
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)    
        if not server:
            plt.show()
        return decorate_plot_result(figure=fig)


class ScoringHistoryTrees(ScoringHistory):

    def _validate_timestep(self, timestep):
        assert_is_type(timestep, "AUTO", "duration", "number_of_trees")
        if timestep == "AUTO":
            timestep = "number_of_trees"
        return timestep


class ScoringHistoryDL(ScoringHistory):

    def _get_scoring_history_to_plot(self):
        scoring_history = self.scoring_history()
        # Delete first row of DL scoring history since it contains NAs & NaNs
        if scoring_history["samples"][0] == 0:
            scoring_history = scoring_history[1:]
        return scoring_history

    def _validate_timestep(self, timestep):
        assert_is_type(timestep, "AUTO", "epochs",  "samples", "duration")
        if timestep == "AUTO":
            timestep = "epochs"
        return timestep


class ScoringHistoryGLM(ScoringHistory):

    _default_metrics_ = {}
    _allowed_metrics_ = dict(
        binomial=["logloss", "auc", "classification_error", "rmse", "objective", "negative_log_likelihood"]
        # for others, validation is done in the plot function below
    )
    
    def scoring_history_plot(self, timestep, metric, server=False, save_plot_path=None):
        plt = get_matplotlib_pyplot(server)
        if plt is None: return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)
        
        scoring_history = self.scoring_history()

        if self.actual_params.get("lambda_search"):
            allowed_timesteps = ["iteration", "duration"]
            allowed_metrics = ["deviance_train", "deviance_test", "deviance_xval"]
            # When provided with multiple alpha values, scoring history contains history of all...
            scoring_history = scoring_history[scoring_history["alpha"] == self._model_json["output"]["alpha_best"]]
        elif self.actual_params.get("HGLM"):
            allowed_timesteps = ["iterations", "duration"]
            allowed_metrics = ["convergence", "sumetaieta02"]
        else:
            allowed_timesteps = ["iterations", "duration"]
            allowed_metrics = ["objective", "negative_log_likelihood"]
        if metric == "AUTO":
            metric = allowed_metrics[0]
        elif metric not in allowed_metrics:
            raise H2OValueError("for {}, metric must be one of: {}".format(self.algo.upper(),
                                                                           ", ".join(allowed_metrics)))

        if timestep == "AUTO":
            timestep = allowed_timesteps[0]
        elif timestep not in allowed_timesteps:
            raise H2OValueError("for {}, timestep must be one of: {}".format(self.algo.upper(),
                                                                             ", ".join(allowed_timesteps)))
        fig = plt.figure()
        plt.xlabel(timestep)
        plt.ylabel(metric)
        plt.title("Validation Scoring History")
        style = "b-" if len(scoring_history[timestep]) > 1 else "bx"
        plt.plot(scoring_history[timestep], scoring_history[metric], style)
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        if not server:
            plt.show()
        return decorate_plot_result(figure=fig)
