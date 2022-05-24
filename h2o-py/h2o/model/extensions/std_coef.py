from h2o.plot import decorate_plot_result, get_matplotlib_pyplot, RAISE_ON_FIGURE_ACCESS
from h2o.utils.typechecks import assert_is_type, I


class StandardCoef:

    def _std_coef_plot(self, num_of_features=None, server=False, save_plot_path=None):
        """
        Plot a GLM model"s standardized coefficient magnitudes.

        :param num_of_features: the number of features shown in the plot.
        :param server: if true set server settings to matplotlib and show the graph
        :param save_plot_path: a path to save the plot via using matplotlib function savefig

        :returns: object that contains the resulting figure (can be accessed using result.figure())
        """
        assert_is_type(num_of_features, None, I(int, lambda x: x > 0))

        plt = get_matplotlib_pyplot(server)
        if not plt: return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)

        # get unsorted tuple of labels and coefficients
        unsorted_norm_coef = self.coef_norm().items()
        # drop intercept value then sort tuples by the coefficient"s absolute value
        drop_intercept = [tup for tup in unsorted_norm_coef if tup[0] != "Intercept"]
        norm_coef = sorted(drop_intercept, key=lambda x: abs(x[1]), reverse=True)

        signage = []
        for element in norm_coef:
            # if positive including zero, color blue, else color orange (use same colors as Flow)
            if element[1] >= 0:
                signage.append("#1F77B4")  # blue
            else:
                signage.append("#FF7F0E")  # dark orange

        # get feature labels and their corresponding magnitudes
        feature_labels = [tup[0] for tup in norm_coef]
        norm_coef_magn = [abs(tup[1]) for tup in norm_coef]
        # specify bar centers on the y axis, but flip the order so largest bar appears at top
        pos = range(len(feature_labels))[::-1]
        # specify the bar lengths
        val = norm_coef_magn

        # check number of features, default is all the features
        if num_of_features is None:
            num_of_features = len(val)

        # plot horizontal plot
        fig, ax = plt.subplots(1, 1, figsize=(14, 10))
        # create separate plot for the case where num_of_features = 1
        if num_of_features == 1:
            plt.barh(pos[0], val[0],
                     align="center", height=0.8, color=signage[0], edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks([0], feature_labels[0])
            ax.margins(None, 0.5)

        else:
            plt.barh(pos[0:num_of_features], val[0:num_of_features],
                     align="center", height=0.8, color=signage[0:num_of_features], edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            ax.margins(None, 0.05)

        # generate custom fake lines that will be used as legend entries:
        # check if positive and negative values exist
        # if positive create positive legend
        if "#1F77B4" in signage[0:num_of_features] and "#FF7F0E" not in signage[0:num_of_features]:
            color_ids = ("Positive",)
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="", markersize=10)
                       for color in set(signage[0:num_of_features])]
            plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)
        # if neg create neg legend
        elif "#FF7F0E" in signage[0:num_of_features] and "#1F77B4" not in signage[0:num_of_features]:
            color_ids = ("Negative",)
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="", markersize=10)
                       for color in set(signage[0:num_of_features])]
            plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)
        # if both provide both colors in legend
        else:
            color_ids = ("Positive", "Negative")
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="", markersize=10)
                       for color in ['#1F77B4', '#FF7F0E']] # blue should always be positive, orange negative
            plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)

        # Hide the right and top spines, color others grey
        ax.spines["right"].set_visible(False)
        ax.spines["top"].set_visible(False)
        ax.spines["bottom"].set_color("#7B7B7B")
        ax.spines["left"].set_color("#7B7B7B")

        # Only show ticks on the left and bottom spines
        plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
        plt.tick_params(axis="x", which="minor", bottom="off", top="off",  labelbottom="off")
        plt.title("Standardized Coef. Magnitudes: H2O GLM", fontsize=20)
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        # show plot
        if server:
            plt.show()
        return decorate_plot_result(figure=fig) 
