#!/usr/bin/python

import sys
import os
import re
from random import random
from random import randrange
from random import uniform


class RealColumn:
    """
    One real number column in a dataset.  May or may not be integer.
    """

    def __init__(self, col_num, col_name, min_val, max_val, na_fraction, is_integer):
        self.col_num = col_num
        self.col_name = col_name
        self.min_val = min_val
        self.max_val = max_val
        self.na_fraction = na_fraction
        self.is_integer = is_integer

    def emit_header(self):
        sys.stdout.write(self.col_name)

    def emit(self):
        if (self.na_fraction == 0):
            is_na = False
        else:
            na_prob = random()
            is_na = na_prob < self.na_fraction

        if (is_na):
            sys.stdout.write("NA")
            return

        if (self.min_val == self.max_val):
            val = self.min_val
            if (self.is_integer):
                val = int(val)
        else:
            if (self.is_integer):
                val = randrange(self.min_val, self.max_val + 1)
            else:
                val = uniform(self.min_val, self.max_val)

        sys.stdout.write(str(val))


class CategoricalColumn:
    """
    One categorical column in a dataset.
    """

    def __init__(self, col_num, col_name, num_levels, na_fraction):
        self.col_num = col_num
        self.col_name = col_name
        self.num_levels = num_levels
        self.na_fraction = na_fraction

    def emit_header(self):
        sys.stdout.write(self.col_name)

    def emit(self):
        if (self.na_fraction == 0):
            is_na = False
        else:
            na_prob = random()
            is_na = na_prob < self.na_fraction

        if (is_na):
            sys.stdout.write("NA")
            return

        if (self.num_levels == 1):
            sys.stdout.write("level0")
            return

        val = randrange(self.num_levels)
        sys.stdout.write("level" + str(val))


class Dataset:
    """
    Object representing the anonymized parsed dataset.
    """

    def __init__(self, parse_log_path):
        """
        Constructor.

        @param parse_log_path: Filesystem path to the parse log output we will use to generate the dataset.
        """
        self.parse_log_path = parse_log_path
        self.dataset_name = None
        self.num_rows = None
        self.columns = []

    def parse(self):
        """
        Parse file specified by constructor.
        """
        f = open(self.parse_log_path, "r")
        self.parse2(f)
        f.close()

    def parse2(self, f):
        """
        Parse file specified by constructor.
        """
        line_num = 0
        s = f.readline()
        while (len(s) > 0):
            line_num += 1

            # Check for beginning of parsed data set.
            match_groups = re.search(r"Parse result for (.*) .(\d+) rows.:", s)
            if (match_groups is not None):
                dataset_name = match_groups.group(1)
                if (self.dataset_name is not None):
                    print("ERROR: Too many datasets found on file {} line {}".format(self.parse_log_path, line_num))
                    sys.exit(1)
                self.dataset_name = dataset_name
                num_rows = int(match_groups.group(2))
                self.num_rows = num_rows
                s = f.readline()
                continue

            # Check for numeric column.
            match_groups = re.search(r"INFO WATER:" +
                                     r"\s*C(\d+):" +
                                     r"\s*numeric" +
                                     r"\s*min\((\S*)\)" +
                                     r"\s*max\((\S*).\)" +
                                     r"\s*(na\((\S+)\))?" +
                                     r"\s*(constant)?",
                                     s)
            if (match_groups is not None):
                col_num = int(match_groups.group(1))
                min_val = float(match_groups.group(2))
                max_val = float(match_groups.group(3))
                # four = match_groups.group(4)
                na_count = match_groups.group(5)
                if (na_count is None):
                    na_count = 0
                else:
                    na_count = int(na_count)
                constant_str = match_groups.group(6)
                is_constant = constant_str is not None
                if (is_constant):
                    if (min_val != max_val):
                        print("ERROR: is_constant mismatch on file {} line {}".format(self.parse_log_path, line_num))
                        sys.exit(1)

                na_fraction = float(na_count) / float(self.num_rows)

                is_min_integer = float(int(min_val)) == float(min_val)
                is_max_integer = float(int(min_val)) == float(min_val)
                is_integer = is_min_integer and is_max_integer

                c = RealColumn(col_num, "C" + str(col_num), min_val, max_val, na_fraction, is_integer)
                self.add_col(c)

                s = f.readline()
                continue

            # Check for categorical column.
            match_groups = re.search(r"INFO WATER:" +
                                     r"\s*C(\d+):" +
                                     r"\s*categorical" +
                                     r"\s*min\((\S*)\)" +
                                     r"\s*max\((\S*).\)" +
                                     r"\s*(na\((\S+)\))?" +
                                     r"\s*(constant)?" +
                                     r"\s*cardinality\((\d+)\)",
                                     s)
            if (match_groups is not None):
                col_num = int(match_groups.group(1))
                min_val = float(match_groups.group(2))
                max_val = float(match_groups.group(3))
                # four = match_groups.group(4)
                na_count = match_groups.group(5)
                if (na_count is None):
                    na_count = 0
                else:
                    na_count = int(na_count)
                constant_str = match_groups.group(6)
                is_constant = constant_str is not None
                if (is_constant):
                    if (min_val != max_val):
                        print("ERROR: is_constant mismatch on file {} line {}".format(self.parse_log_path, line_num))
                        sys.exit(1)
                num_levels = int(match_groups.group(7))
                if (is_constant):
                    if (num_levels != 1):
                        print("ERROR: num_levels mismatch on file {} line {}".format(self.parse_log_path, line_num))
                        sys.exit(1)
                na_fraction = float(na_count) / float(self.num_rows)

                c = CategoricalColumn(col_num, "C" + str(col_num), num_levels, na_fraction)
                self.add_col(c)

                s = f.readline()
                continue

            print("ERROR: Unrecognized regexp pattern on file {} line {}".format(self.parse_log_path, line_num))
            sys.exit(1)

    def add_col(self, c):
        self.columns.append(c)

    def emit_header(self):
        columns = self.columns
        first = True
        for c in columns:
            if (not first):
                sys.stdout.write(",")
            c.emit_header()
            first = False
        sys.stdout.write("\n")
        sys.stdout.flush()

    def emit_one_row(self):
        columns = self.columns
        first = True
        for c in columns:
            if (not first):
                sys.stdout.write(",")
            c.emit()
            first = False
        sys.stdout.write("\n")
        sys.stdout.flush()


# --------------------------------------------------------------------
# Main program
#--------------------------------------------------------------------

# Global variables that can be set by the user.
g_script_name = None
g_parse_log_path = None
g_num_rows = 1


def usage():
    print("")
    print("This program takes the log output from H2O's parse and generates")
    print("a random dataset with similar characteristics.")
    print("")
    print("Usage:  " + g_script_name +
          " [--rows num_rows_to_generate]" +
          " -f parse_log_output_file")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def bad_arg(s):
    print("")
    print("ERROR: Illegal use of (otherwise valid) argument: " + s)
    print("")
    usage()


def parse_args(argv):
    global g_parse_log_path
    global g_num_rows

    i = 1
    while (i < len(argv)):
        s = argv[i]

        if (s == "-f"):
            i += 1
            if (i > len(argv)):
                usage()
            g_parse_log_path = argv[i]
        elif (s == "--rows"):
            i += 1
            if (i > len(argv)):
                usage()
            g_num_rows = int(argv[i])
        elif (s == "-h" or s == "--h" or s == "-help" or s == "--help"):
            usage()
        else:
            unknown_arg(s)

        i += 1


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name
    global g_parse_log_path

    g_script_name = os.path.basename(argv[0])

    parse_args(argv)

    if (g_parse_log_path is None):
        print("")
        print("ERROR: -f not specified")
        usage()

    d = Dataset(g_parse_log_path)
    d.parse()

    d.emit_header()
    for i in range(0, g_num_rows):
        d.emit_one_row()


if __name__ == "__main__":
    main(sys.argv)
