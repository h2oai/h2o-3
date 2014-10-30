# TODO: ugh:
import sys, pprint, argparse, string, errno

sys.path.extend(['.','py'])
import h2o, h2o_util
import os

# https://github.com/joeyespo/grip
# Transform GitHub-flavored Markdown to HTML
from grip import export

# print "ARGV is:", sys.argv

here=os.path.dirname(os.path.realpath(__file__))

parser = argparse.ArgumentParser(
    description='Attach to an H2O instance and call its REST API to generate the REST API docs and write them to the filesystem.',
)
parser.add_argument('--verbose', '-v', help='verbose output', action='store_true')
parser.add_argument('--usecloud', help='ip:port to attach to', default='')
parser.add_argument('--host', help='hostname to attach to', default='localhost')
parser.add_argument('--port', help='port to attach to', type=int, default=54321)
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/docs/REST'))
parser.add_argument('--github_user', help='github user, for Markdown -> HTML rendering')
parser.add_argument('--github_password', help='github password, for Markdown -> HTML rendering')
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

endpoints = a_node.endpoints()['routes']

print 'creating the endpoint docs. . .'
if h2o.H2O.verbose:
    print 'Endpoints: '
    pp.pprint(endpoints)

for num in range(len(endpoints)):
    meta = a_node.endpoint_by_number(num)['routes'][0]
    url_pattern = meta['url_pattern']
    markdown = meta['markdown']
    markdown = string.replace(markdown, '\\n', '\n')
    
    save_name = url_pattern
    save_name = string.replace(save_name, '^(/v?\\d+)?', '/N')
    save_name = string.replace(save_name, '(?<', '{')
    save_name = string.replace(save_name, '>.*)', '}')
    save_name = string.replace(save_name, '>[0-9]+)', '}')

    if len(save_name) == 0:
        print 'save name is empty for: '
        pp.pprint(meta)

    save_full_md = args.dest + os.sep + 'markdown' + save_name + '.md'  # assumes the pattern starts with / after the replaces above
    save_dir_md = os.path.dirname(save_full_md)

    save_full_html = args.dest + os.sep + 'html' + save_name + '.html'  # assumes the pattern starts with / after the replaces above
    save_dir_html = os.path.dirname(save_full_html)

    # create dirs without race:
    try:
        os.makedirs(save_dir_md)
        os.makedirs(save_dir_html)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full_md, 'w') as the_file:
        the_file.write(markdown)

    # use grip to render the .md to .html
    export(path=save_full_md, gfm=True, out_filename=save_full_html, username=args.github_user, password=args.github_password)

