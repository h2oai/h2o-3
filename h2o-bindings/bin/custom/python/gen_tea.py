def class_extensions():

    # didn't work:
    # def __init__(self, special_vals, **kwargs):
    #     from h2o.utils.shared_utils import stringify_dict_as_map
    #     super(H2OTeaEstimator, self).__init__()
    #     self._parms = {}
    #     self.special_vals = dict(special_vals)
    #     self._parms['special_values'] =  None if self.special_vals is None else stringify_dict_as_map(self.special_vals) # unique to grid search
    #     for pname, pvalue in kwargs.items():
    #         if pname == 'model_id':
    #             self._id = pvalue
    #             self._parms["model_id"] = pvalue
    #         elif pname in self.param_names:
    #             # Using setattr(...) will invoke type-checking of the arguments
    #             setattr(self, pname, pvalue)
    #         else:
    #             raise H2OValueError("Unknown parameter %s = %r" % (pname, pvalue))

    def variable_rank_count_plot(self, variable, png_path=None):
        data = self._get_variable_rank_count_plot_data(variable)
        if png_path != None:
            import matplotlib.pyplot as plt
            self._plot_variable_rank_count(data.as_data_frame().get_values(), png_path)
        return data


    def _get_variable_rank_count_plot_data(self, variable):
        import h2o
        j = h2o.api("POST /3/VariableRankCountPlot",
                    data={"overall_metrics": self._model_json["output"]['overall_metrics']['frame_id']['name'], "variable": variable})
        return h2o.get_frame(j['plot_output']['name'])



    def _get_variable_inclusion_plot_data(self):
        import h2o
        j = h2o.api("POST /3/VariableInclusionPlot",
                    data={"plot_input": self._model_json["output"]['overall_metrics']['frame_id']['name']})
        return h2o.get_frame(j['plot_output']['name'])

    def variable_inclusion_plot(self, png_path=None):
        data = self._get_variable_inclusion_plot_data()
        if png_path != None:
            import matplotlib.pyplot as plt
            plt.figure(figsize=(10, 10))
            data_as_df = data.as_data_frame().get_values()
            self._plot_variable_inclusion(data_as_df, variable_names=data.names)
            plt.savefig(png_path)
            plt.show()
        return data

    def _plot_variable_inclusion(self, data, variable_names):
        import numpy as np
        import matplotlib.pyplot as plt
        ny = len(data[0])
        ind = list(range(ny))

        axes = []
        cum_size = np.zeros(ny)
        data = np.array(data)
        labels = ['1','2','3']
        colors=['royalblue', 'cornflowerblue', 'lightsteelblue']

        for i, row_data in enumerate(data):
            if (i > 0):
                color = colors[i - 1] if colors is not None else None
                axes.append(plt.bar(ind, row_data, bottom=cum_size,
                                    label=labels[i - 1], color=color))
                cum_size += row_data

        plt.xticks(ind, variable_names, rotation=40, horizontalalignment='right')
        plt.ylabel("Number of Records")
        plt.xlabel("Variable")
        plt.title('Variable Inclusion')

        for axis in axes:
            for bar in axis:
                w, h = bar.get_width(), bar.get_height()
                plt.text(bar.get_x() + w/2, bar.get_y() + h/2,
                         axis._label, ha="center",
                         va="center")




    def record_level_variable_simulation_plot(self, record_id_value, variable, png_path=None):
        import h2o
        sampling_var_id = self._model_json['output']['sampling_variables'].index(variable)
        sampled_score_by_var = h2o.get_frame(self._model_json['output']['sampled_scored_data_keys'][sampling_var_id]['name'])
        data = self._get_record_level_variable_simulation_plot_data(sampled_score_by_var, record_id_value, variable)
        if png_path != None:
            import matplotlib.pyplot as plt
            self._plot_record_level_variable_simulation(data.as_data_frame().get_values(), png_path)
        return data

    def _plot_record_level_variable_simulation(self, data, png_path):
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt

        x = data[:,2]
        y_sampled_score = data[:,4]
        y_actual_score = data[:,3]

        lists = sorted(zip(*[x, y_sampled_score]))
        new_x, new_y_sampled_score =  zip(*sorted(zip(x, y_sampled_score)))  #list(zip(*lists))
        plt.plot(x, y_actual_score, label = "Actual Score", color="grey")
        plt.plot(new_x, new_y_sampled_score, label = "Sampled Score")

        plt.xlabel("Variable Bins")
        plt.ylabel("Score")
        plt.legend(loc='upper right')
        plt.title("Record Level Variable Simulation for record:" + str(data[0,0]) + " and variable " + data[0,1])
        plt.savefig(png_path, bbox_inches='tight')
        plt.close('all')

    def _get_record_level_variable_simulation_plot_data(self, sampled_score_by_var, record_id_value, variable):
        import h2o
        j = h2o.api("POST /3/RecordLevelVariableSimulationPlot",
                    data={"sampled_score_by_var": sampled_score_by_var.key, "record_id_value": record_id_value, "variable": variable})
        return h2o.get_frame(j['plot_output']['name'])



    def _get_explanations_plot_data(self):
        # plotData = Tea.getDataForExplanationsPlot(plotInput, plotScoreData, "ID");
        import h2o
        j = h2o.api("POST /3/ExplanationsPlot",
                    data={"overall_metrics": self._model_json["output"]['overall_metrics']['frame_id']['name'], "scored_data": self._model_json['output']['scored_data']['frame_id']['name'], "record_id": self.actual_params['record_id']})
        return h2o.get_frame(j['plot_output']['name'])

    def explanations_plot(self, png_path=None):
        data = self._get_explanations_plot_data()
        if png_path != None:
            import matplotlib.pyplot as plt
            self._plot_explanations(data.as_data_frame().get_values(), png_path)
        return data


    def _plot_explanations(self, data, png_path):
        import matplotlib
        matplotlib.use('Agg')
        import numpy as np
        import matplotlib.pyplot as plt

        y_pos = np.arange(len(data[:,0]))
        bar_width = .55

        fig, axs = plt.subplots(1,2, figsize=(18,len(data[:,1])/5))
        fig.subplots_adjust(wspace=0, top=1, right=1, left=0, bottom=0)

        axs[1].barh(y_pos[::-1], data[:,3][::-1], bar_width,  align='center', alpha=0.4, color='royalblue', tick_label=data[:,3][::-1])
        axs[1].set_yticks([])
        axs[1].set_xlabel('Upside')
        axs[1].set_ylim(0 - .4, (len(data[:,1])) + .4)

        for i, v in enumerate( data[:,3]):
            axs[1].text(v, i, " " + str(v), va='center')

        cell_text = list(zip(data[:,0], data[:,2], data[:,1], data[:,4], data[:,5]))
        column_labels = ['RecordId', 'ActualScore',
                         "Variable", "Rank", "Value",
                         ]

        axs[0].axis('off')

        the_table = axs[0].table(cellText=cell_text,
                                 colLabels=column_labels,
                                 bbox=[0.4, 0.0, 0.6, 1.0])

        for i in range(len(data[:,1])):
            if the_table[i,0].get_text()._text == the_table[i + 1,0].get_text()._text and the_table[i,1].get_text()._text == the_table[i + 1,1].get_text()._text:
                the_table[i,0].visible_edges = 'RL'
                the_table[i,1].visible_edges = 'RL'
                the_table[i+1,0].visible_edges = 'RL'
                the_table[i+1,1].visible_edges = 'RL'
            if (i == len(data[:,1]) - 1):
                the_table[i+1,0].visible_edges = 'RLB'
                the_table[i+1,1].visible_edges = 'RLB'
            else:
                if(i != 0):
                    the_table[i,0].visible_edges = 'BRL'
                    the_table[i,1].visible_edges = 'BRL'

        plt.title("Explanations")

        plt.savefig(png_path, bbox_inches='tight')
        plt.show()
        plt.close('all')



    def _plot_variable_rank_count(self, data, png_path):
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt


        fig, axs = plt.subplots(figsize=(15,len(data[:,1])))
        fig.subplots_adjust(wspace=0, top=1, right=1, left=0, bottom=0)

        formatter = "{:10.2f}"

        variable = [data[0,0], data[0,0], data[0,0], data[0,0], data[0,0]]
        bins = ["[0, " + formatter.format(data[0,31]) + "]",
                "(" + formatter.format(data[0,31]) + ", " + formatter.format(data[0,32]) + "]",
                "(" + formatter.format(data[0,32]) + ", " + formatter.format(data[0,33]) + "]",
                "(" + formatter.format(data[0,33]) + ", " + formatter.format(data[0,34]) + "]",
                "(" + formatter.format(data[0,34]) + " , 1]"]
        rank1bins = [data[0,1], data[0,2], data[0,3], data[0,4], data[0,5]]
        rank2bins = [data[0,6], data[0,7], data[0,8], data[0,9], data[0,10]]
        rank3bins = [data[0,11], data[0,12], data[0,13], data[0,14], data[0,15]]
        rank4bins = [data[0,16], data[0,17], data[0,18], data[0,19], data[0,20]]
        rank5bins = [data[0,21], data[0,22], data[0,23], data[0,24], data[0,25]]
        rank6bins = [data[0,26], data[0,27], data[0,28], data[0,29], data[0,30]]

        cell_text = list(zip(variable, bins, rank1bins, rank2bins, rank3bins, rank4bins, rank5bins, rank6bins))

        column_labels = ['Variable','Bin' ,'Rank 1', "Rank 2", "Rank 3", "Rank 4", "Rank 5", "Rank 6"]

        axs.axis('off')
        axs.table(cellText=cell_text, colLabels=column_labels)

        plt.title("Variable Rank Count Plot: " + data[0,0])
        plt.savefig(png_path, bbox_inches='tight')
        plt.show()
        plt.close('all')

extensions = dict(
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Builds a RuleFit on a parsed dataset, for regression or 
classification. 
"""
)
