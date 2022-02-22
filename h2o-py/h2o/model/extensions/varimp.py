from h2o.plot import decorate_plot_result, get_matplotlib_pyplot, RAISE_ON_FIGURE_ACCESS
from h2o.utils.typechecks import assert_is_type


class VariableImportance:

    def _varimp_plot(self, num_of_features=None, server=False, save_plot_path=None):
        """
        Plot the variable importance for a trained model.

        :param num_of_features: the number of features shown in the plot (default is 10 or all if less than 10).
        :param server: if true set server settings to matplotlib and show the graph
        :param save_plot_path: a path to save the plot via using matplotlib function savefig
        :return: object that contains the resulting figure (can be accessed using result.figure())

        :returns: None.
        """
        assert_is_type(num_of_features, None, int)
        assert_is_type(server, bool)

        plt = get_matplotlib_pyplot(server)
        if plt is None:
            return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)

        # get the variable importances as a list of tuples, do not use pandas dataframe
        importances = self.varimp(use_pandas=False)
        # features labels correspond to the first value of each tuple in the importances list
        feature_labels = [tup[0] for tup in importances]
        # relative importances correspond to the first value of each tuple in the importances list
        scaled_importances = [tup[2] for tup in importances]
        # specify bar centers on the y axis, but flip the order so largest bar appears at top
        pos = range(len(feature_labels))[::-1]
        # specify the bar lengths
        val = scaled_importances

        # default to 10 or less features if num_of_features is not specified
        if num_of_features is None:
            num_of_features = min(len(val), 10)

        fig, ax = plt.subplots(1, 1, figsize=(14, 10))
        # create separate plot for the case where num_of_features == 1
        if num_of_features == 1:
            plt.barh(pos[0:num_of_features], val[0:num_of_features], align="center",
                     height=0.8, color="#1F77B4", edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            ax.margins(None, 0.5)

        else:
            plt.barh(pos[0:num_of_features], val[0:num_of_features], align="center",
                     height=0.8, color="#1F77B4", edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            plt.ylim([min(pos[0:num_of_features])- 1, max(pos[0:num_of_features])+1])
            # ax.margins(y=0.5)

        # check which algorithm was used to select right plot title
        plt.title("Variable Importance: H2O %s" % self._model_json["algo_full_name"], fontsize=20)
        if not server:
            plt.show()
            
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)

        return decorate_plot_result(figure=plt.gcf())    
