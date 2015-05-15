#!/usr/bin/python

import sys
import os
import requests
import urllib
import re
import dateutil.parser


g_user = None
g_pass = None
g_sprint = None
g_csv = False
g_verbose = False
g_burndown = False
g_start_date = None


def get_issue_key(issue):
    return issue[u'key']


def get_issue_assignee(issue):
    return issue[u'fields'][u'assignee']


def get_issue_assignee_name(issue):
    return issue[u'fields'][u'assignee'][u'name']


def get_issue_story_points(issue):
    story_points = issue[u'fields'][u'customfield_10004']
    if story_points is None:
        story_points = 0
    else:
        story_points = float(story_points)
    return story_points


def get_issue_resolution_date(issue):
    d = dateutil.parser.parse(issue[u'fields'][u'resolutiondate'], ignoretz=True)
    return d


class Person:
    def __init__(self, name):
        self.name = name
        self.resolved_list = []
        self.resolved_story_points = 0
        self.unresolved_list = []
        self.unresolved_story_points = 0

    def add(self, issue):
        story_points = get_issue_story_points(issue)
        resolution = issue[u'fields'][u'resolution']
        if resolution is None:
            self.unresolved_story_points += story_points
            self.unresolved_list.append(issue)
        else:
            self.resolved_story_points += story_points
            self.resolved_list.append(issue)

    def get_story_points(self):
        return (self.resolved_story_points + self.unresolved_story_points)

    def get_total_issues_count(self):
        return (len(self.resolved_list) + len(self.unresolved_list))

    def emit_issues_csv(self):
        print (self.name + "," + str(len(self.resolved_list)) + "," + str(len(self.unresolved_list)))

    def emit_story_points_csv(self):
        print (self.name + "," + str(self.resolved_story_points) + "," + str(self.unresolved_story_points))

    def emit_barchart(self):
        print("")
        print("-----" + self.name + "-----")
        Person._print_bar("  resolved", self.resolved_story_points, "R")
        if (g_verbose):
            print("")
            for issue in self.resolved_list:
                story_points = get_issue_story_points(issue)
                summary = issue[u'fields'][u'summary']
                summary = summary.encode('ascii', 'ignore')
                print("{0:14s}{1:11s} ({2:.1f}): {3}".format("", issue[u'key'], story_points, summary))
        if (g_verbose):
            print("")
        Person._print_bar("unresolved", self.unresolved_story_points, "U")
        if (g_verbose):
            print("")
            for issue in self.unresolved_list:
                story_points = get_issue_story_points(issue)
                summary = issue[u'fields'][u'summary']
                summary = summary.encode('ascii', 'ignore')
                print("{0:14s}{1:11s} ({2:.1f}): {3}".format("", issue[u'key'], story_points, summary))

    @staticmethod
    def _print_bar(label, value, char):
        bars_per_day = 4
        sys.stdout.write(label + ":  ")
        num_bars = int(value * bars_per_day)
        i = 0
        while (num_bars > 0):
            if (i % bars_per_day == 0):
                sys.stdout.write(" ")
            sys.stdout.write(char)
            num_bars -= 1
            i += 1
        sys.stdout.write("\n")


class PeopleManager:
    def __init__(self):
        self.people_map = {}

    def add(self, issue):
        assignee = get_issue_assignee(issue)
        if (assignee is None):
            print("ERROR: assignee is none for issue: " + str(issue))
            sys.exit(1)
        assignee_name = issue[u'fields'][u'assignee'][u'name']
        person = self.find(assignee_name)
        person.add(issue)

    def find(self, name):
        if name not in self.people_map:
            person = Person(name)
            self.people_map[name] = person
        person = self.people_map[name]
        return person

    def emit(self):
        if g_burndown:
            total_story_points = 0
            total_issues = 0
            for key in sorted(self.people_map.keys()):
                person = self.people_map[key]
                total_story_points += person.get_story_points()
                total_issues += person.get_total_issues_count()
            issues = self.get_resolved_issues_sorted_by_date()
            if (len(issues) == 0):
                print("No resolved issues")
                return
            after_total_story_points = total_story_points
            after_total_issues = total_issues
            earliest_resolution_date = get_issue_resolution_date(issues[0])
            if (g_start_date is not None):
                start_date = g_start_date
            else:
                start_date = earliest_resolution_date
            if g_csv:
                print("key,assignee,story_points,before_total_story_points,resolution_date,resolution_date_days,"
                      + "after_total_story_points,after_total_issues")
                if (start_date < earliest_resolution_date):
                    tmp = start_date
                else:
                    tmp = earliest_resolution_date
                date_str = tmp.strftime("%Y-%m-%d %H:%M")
                date_days = (tmp - start_date).total_seconds() / 3600.0 / 24
                print(",,,,{},{},{},{}".format(date_str, date_days, total_story_points, total_issues))
            for issue in issues:
                before_total_story_points = after_total_story_points
                before_total_issues = after_total_issues
                key = get_issue_key(issue)
                assignee = get_issue_assignee_name(issue)
                story_points = get_issue_story_points(issue)
                resolution_date = get_issue_resolution_date(issue)
                date_days = (resolution_date - start_date).total_seconds() / 3600.0 / 24
                after_total_story_points = before_total_story_points - story_points
                after_total_issues = before_total_issues - 1
                if g_csv:
                    date_str = resolution_date.strftime("%Y-%m-%d %H:%M")
                    print("{},{},{},{},{},{},{},{}"
                          .format(key, assignee,
                                  story_points, before_total_story_points,
                                  date_str, date_days,
                                  after_total_story_points,
                                  after_total_issues))
                else:
                    date_str = resolution_date.strftime("%Y-%m-%d %H:%M")
                    print("{:11s}  {:10s}  {:.1f}  {:.1f}  {:s}  {:.2f}  {:.1f}  {:.1f}"
                          .format(key, assignee,
                                  story_points, before_total_story_points,
                                  date_str, date_days,
                                  after_total_story_points,
                                  after_total_issues))
            return

        if g_csv:
            print("STORY_POINTS")
            print("name,resolved,unresolved")
            for key in sorted(self.people_map.keys()):
                person = self.people_map[key]
                person.emit_story_points_csv()
            print("")
            print("NUMBER_OF_ISSUES")
            print("name,resolved,unresolved")
            for key in sorted(self.people_map.keys()):
                person = self.people_map[key]
                person.emit_issues_csv()
        else:
            for key in sorted(self.people_map.keys()):
                person = self.people_map[key]
                person.emit_barchart()

    def get_resolved_issues_sorted_by_date(self):
        issues = []
        for person in self.people_map.values():
            for i in person.resolved_list:
                issues.append(i)
        sorted_issues = sorted(issues, key=lambda x: get_issue_resolution_date(x))
        return sorted_issues


def usage():
    print("")
    print("usage:  " + g_script_name + " --user username --pass password --sprint sprintname")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def parse_config_file():
    global g_user
    global g_pass
    global g_sprint
    global g_start_date
    home = os.path.expanduser("~")
    config_file = os.path.join(home, ".h2o_jira")
    if (os.path.exists(config_file)):
        with open(config_file) as f:
            for line in f:
                match_groups = re.search(r"\s*(\S+)\s*=\s*([\S\s]+)\s*", line.rstrip())
                if (match_groups is not None):
                    key = match_groups.group(1)
                    value = match_groups.group(2)
                    if (key == "user"):
                        g_user = value
                    elif (key == "pass"):
                        g_pass = value
                    elif (key == "sprint"):
                        g_sprint = value
                    elif (key == "startdate"):
                        g_start_date = dateutil.parser.parse(value)


def parse_args(argv):
    global g_user
    global g_pass
    global g_sprint
    global g_csv
    global g_verbose
    global g_burndown
    global g_start_date

    i = 1
    while (i < len(argv)):
        s = argv[i]

        if (s == "--user"):
            i += 1
            if (i > len(argv)):
                usage()
            g_user = argv[i]
        elif (s == "--pass"):
            i += 1
            if (i > len(argv)):
                usage()
            g_pass = argv[i]
        elif (s == "--sprint"):
            i += 1
            if (i > len(argv)):
                usage()
            g_sprint = argv[i]
        elif (s == "--csv"):
            g_csv = True
        elif (s == "-v" or s == "--verbose"):
            g_verbose = True
        elif (s == "--burndown"):
            g_burndown = True
        elif (s == "--startdate"):
            i += 1
            if (i > len(argv)):
                usage()
            g_start_date = dateutil.parser.parse(argv[i])
        elif (s == "-h" or s == "--h" or s == "-help" or s == "--help"):
            usage()
        else:
            unknown_arg(s)

        i += 1

    if (g_user is None):
        print "ERROR: user is not specified"
        usage()

    if (g_pass is None):
        print "ERROR: pass is not specified"
        usage()

    if (g_sprint is None):
        print "ERROR: sprint is not specified"
        usage()


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name

    g_script_name = os.path.basename(argv[0])
    parse_config_file()
    parse_args(argv)

    url = 'https://0xdata.atlassian.net/rest/api/2/search?jql=sprint="' + urllib.quote(g_sprint) + '"&maxResults=1000'
    r = requests.get(url, auth=(g_user, g_pass))
    if (r.status_code != 200):
        print("ERROR: status code is " + str(r.status_code))
        sys.exit(1)
    j = r.json()
    issues = j[u'issues']
    pm = PeopleManager()
    for issue in issues:
        pm.add(issue)

    pm.emit()
    print("")

if __name__ == "__main__":
    main(sys.argv)
