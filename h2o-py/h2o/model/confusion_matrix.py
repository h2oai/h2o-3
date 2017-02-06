# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.two_dim_table import H2OTwoDimTable
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type


class ConfusionMatrix(object):
    ROUND = 4  # round count_errs / sum

    def __init__(self, cm, domains=None, table_header=None):
        assert_is_type(cm, list)

        if len(cm) == 2: cm = list(zip(*cm))  # transpose if 2x2
        nclass = len(cm)
        class_errs = [0] * nclass
        class_sums = [0] * nclass
        class_err_strings = [0] * nclass
        cell_values = [[0] * (1 + nclass)] * (1 + nclass)
        totals = [sum(c) for c in cm]
        total_errs = 0
        for i in range(nclass):
            class_errs[i] = sum([v[i] for v in cm[:i] + cm[(i + 1):]])
            total_errs += class_errs[i]
            class_sums[i] = sum([v[i] for v in cm])  # row sums
            class_err_strings[i] = \
                " (" + str(class_errs[i]) + "/" + str(class_sums[i]) + ")"
            class_errs[i] = float("nan") if class_sums[i] == 0 else round(class_errs[i] / class_sums[i], self.ROUND)
            # and the cell_values are
            cell_values[i] = [v[i] for v in cm] + [str(class_errs[i])] + [class_err_strings[i]]

        # tally up the totals
        class_errs += [sum(class_errs)]
        totals += [sum(class_sums)]
        class_err_strings += [" (" + str(total_errs) + "/" + str(totals[-1]) + ")"]

        class_errs[-1] = float("nan") if totals[-1] == 0 else round(total_errs / totals[-1], self.ROUND)

        # do the last row of cell_values ... the "totals" row
        cell_values[-1] = totals[0:-1] + [str(class_errs[-1])] + [class_err_strings[-1]]

        if table_header is None: table_header = "Confusion Matrix (Act/Pred)"
        col_header = [""]  # no column label for the "rows" column
        if domains is not None:
            import copy
            row_header = copy.deepcopy(domains)
            col_header += copy.deepcopy(domains)
        else:
            row_header = [str(i) for i in range(nclass)]
            col_header += [str(i) for i in range(nclass)]

        row_header += ["Total"]
        col_header += ["Error", "Rate"]

        for i in range(len(row_header)):
            cell_values[i].insert(0, row_header[i])

        self.table = H2OTwoDimTable(row_header=row_header, col_header=col_header,
                                    table_header=table_header, cell_values=cell_values)


    def show(self):
        """Print the confusion matrix into the console."""
        self.table.show()


    def __repr__(self):
        self.show()
        return ""


    def to_list(self):
        """Convert this confusion matrix into a 2x2 plain list of values."""
        return [[int(self.table.cell_values[0][1]), int(self.table.cell_values[0][2])],
                [int(self.table.cell_values[1][1]), int(self.table.cell_values[1][2])]]


    @staticmethod
    def read_cms(cms=None, domains=None):
        """Read confusion matrices from the list of sources (?)."""
        assert_is_type(cms, [list])
        return [ConfusionMatrix(cm, domains) for cm in cms]
