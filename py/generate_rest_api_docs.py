# TODO: ugh:
import sys, pprint, argparse, string, errno, sets

sys.path.extend(['.','py'])
import h2o, h2o_util
import os

# print "ARGV is:", sys.argv

here=os.path.dirname(os.path.realpath(__file__))

parser = argparse.ArgumentParser(
    description='Attach to an H2O instance and call its REST API to generate the REST API docs and write them to the filesystem.',
)
parser.add_argument('--verbose', '-v', help='verbose output', action='store_true')
parser.add_argument('--usecloud', help='ip:port to attach to', default='')
parser.add_argument('--host', help='hostname to attach to', default='localhost')
parser.add_argument('--port', help='port to attach to', type=int, default=54321)
parser.add_argument('--dest', help='destination directory', default=(here + '/../h2o-docs'))
parser.add_argument('--generate_html', help='translate the Markdown to HTML', action='store_true', default=False)
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

endpoints = a_node.endpoints()
schemas = a_node.schemas()

#
# Write the raw responses from /endpoints and /schemas to
# ../h2o-docs, so that they're picked up by ../h2o-web/help.coffee
#
endpoints_json_filename = args.dest + os.sep + 'routes.json'
endpoints_json_text = endpoints['__http_response']['text']
with open(endpoints_json_filename, 'w') as the_file:
    the_file.write(endpoints_json_text)

schemas_json_filename = args.dest + os.sep + 'schemas.json'
schemas_json_text = schemas['__http_response']['text']
with open(schemas_json_filename, 'w') as the_file:
    the_file.write(schemas_json_text)
