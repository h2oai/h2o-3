#! /usr/bin/python

import argparse
import subprocess
import os
import sys

DISTRIBUTION_CDH = 'CDH'
SUPPORTED_CDH_VERSIONS = ['5.4', '5.5', '5.6', '5.7', '5.8', '5.10']
DISTRIBUTION_HDP = 'HDP'
SUPPORTED_HDP_VERSIONS = ['2.2', '2.3', '2.4', '2.5', '2.6']
SUPPORTED_DISTRIBUTIONS = [DISTRIBUTION_CDH, DISTRIBUTION_HDP]

def pretty_print_array(array_to_print):
    if array_to_print is None or len(array_to_print) == 0:
        return ""
    if len(array_to_print) == 1:
        return str(array_to_print[0])
    else:
        return "%s and %s" % (", ".join(array_to_print[:-1]), array_to_print[-1])


def init_args_parser():
    supported_distributions_text = pretty_print_array(SUPPORTED_DISTRIBUTIONS)
    parser = argparse.ArgumentParser(description='Builds a Dockerfile for given Hadoop distribution and version.')
    parser.add_argument('-d', '--distribution',
        type=str, choices=SUPPORTED_DISTRIBUTIONS,
        help='Distribution of Hadoop. Currently %s are supported.' % supported_distributions_text,
        required=True
    )
    parser.add_argument('-v', '--version', type=str,
        help='Version of the Hadoop distribution.', required=True
    )
    return parser

def validate_version(distribution, version):
    message = 'Version %s of %s not supported. Supported versions of %s are %s'
    if distribution == DISTRIBUTION_CDH:
        if not version in SUPPORTED_CDH_VERSIONS:
            parser.error(message % (version, distribution, distribution, pretty_print_array(SUPPORTED_CDH_VERSIONS)))
    elif distribution == DISTRIBUTION_HDP:
        if not version in SUPPORTED_HDP_VERSIONS:
            parser.error(message % (version, distribution, distribution, pretty_print_array(SUPPORTED_HDP_VERSIONS)))
    else:
        raise ValueError('Distribution %s not supported' % distribution)

if __name__ == '__main__':
    parser = init_args_parser()
    args = parser.parse_args()
    validate_version(args.distribution, args.version)
    print("Building Docker image for %s version %s" % (args.distribution, args.version))
    cmd = "docker build -t h2o-%s:%s --build-arg VERSION=%s --build-arg PATH_PREFIX=%s -f %s/Dockerfile ." % (args.distribution.lower(), args.version, args.version, args.distribution.lower(), args.distribution.lower())
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stdin=subprocess.PIPE)
    for line in iter(process.stdout.readline, ''):
        sys.stdout.write(line)
