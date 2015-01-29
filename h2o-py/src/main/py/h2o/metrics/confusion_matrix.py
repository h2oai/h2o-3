"""
A confusion matrix from H2O.
"""

from ..two_dim_table import H2OTwoDimTable


class ConfusionMatrix(object):

    ROUND = 4  # round count_errs / sum

    def __init__(self, cm=None, domains=None):

        if not cm:
            raise ValueError("Missing data, `cm_raw` is None")

        if not isinstance(cm, list):
            raise ValueError("`cm` is not a list. Got: " + type(cm))

        nclass = len(cm)
        class_errs = [0] * nclass
        class_sums = [0] * nclass
        class_err_strings = [0] * nclass
        cell_values = [[0] * (1 + nclass)] * (1 + nclass)
        totals = [sum(c) for c in cm]
        for i in range(nclass):
            class_errs[i] = sum([v[i] for v in cm[:i] + cm[(i + 1):]])
            class_sums[i] = sum([v[i] for v in cm])  # row sums
            class_err_strings[i] = \
                " (" + str(class_errs[i]) + "/" + str(class_sums[i]) + ")"
            if class_sums[i] == 0:
                class_err = float("nan")
            else:
                class_err = \
                    round(float(class_errs[i]) / float(class_sums[i]), self.ROUND)
            class_err_strings[i] = str(class_err) + class_err_strings[i]

            # and the cell_values are
            cell_values[i] = [v[i] for v in cm] + [class_err_strings[i]]

        # tally up the totals
        class_errs += [sum(class_errs)]
        totals += [sum(class_sums)]
        class_err_strings += [" (" + str(class_errs[-1]) + "/" + str(totals[-1]) + ")"]

        if totals[-1] == 0:
            class_err = float("nan")
        else:
            class_err = round(float(class_errs[-1]) / float(totals[-1]), self.ROUND)
        class_err_strings[-1] = str(class_err) + class_err_strings[-1]

        # do the last row of cell_values ... the "totals" row
        cell_values[-1] = totals[0:-1] + [class_err_strings[-1]]

        table_header = "Confusion Matrix (Act/Pred)"

        if domains:
            row_header = domains
            col_header = domains
        else:
            row_header = [str(i) for i in range(nclass)]
            col_header = [str(i) for i in range(nclass)]

        row_header += ["Totals"]
        col_header += ["Error"]

        self.table = H2OTwoDimTable(row_header=row_header, col_header=col_header,
                                    table_header=table_header, cell_values=cell_values)

    def show(self):
        self.table.show()

    def __repr__(self):
        self.show()
        return ""

    @staticmethod
    def read_cms(cms=None, domains=None):
        if cms is None:
            raise ValueError("Missing data, no `cms`.")

        if not isinstance(cms, list):
            raise ValueError("`cms` must be a list of lists")

        lol_all = all(isinstance(l, (tuple, list)) for l in cms)

        if not lol_all:
            raise ValueError("`cms` must be a list of lists")

        return [ConfusionMatrix(cm, domains) for cm in cms]