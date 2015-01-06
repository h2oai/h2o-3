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
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/docs/REST'))
parser.add_argument('--generate_html', help='translate the Markdown to HTML', action='store_true', default=False)
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

endpoints_result = a_node.endpoints()
endpoints = endpoints_result['routes']

print 'creating the endpoint docs. . .'
if h2o.H2O.verbose:
    print 'Endpoints: '
    pp.pprint(endpoints)

endpoints_meta = []
schemas = sets.Set()
for num in range(len(endpoints)):
    meta = a_node.endpoint_by_number(num)['routes'][0]

    endpoints_meta.append(meta)
    schemas.add(meta['input_schema'])
    schemas.add(meta['output_schema'])

    url_pattern = meta['url_pattern']
    markdown = meta['markdown']
    markdown = string.replace(markdown, '\\n', '\n')
    
    save_name = url_pattern
    save_name = string.replace(save_name, '^(/\\d+)?', '/N')
    save_name = string.replace(save_name, '^(/v?\\d+)?', '/N')
    save_name = string.replace(save_name, '(?<', '{')
    save_name = string.replace(save_name, '>.*)', '}')
    save_name = string.replace(save_name, '>[0-9]+)', '}')

    if len(save_name) == 0:
        print 'save name is empty for: '
        pp.pprint(meta)

    save_full_md = args.dest + os.sep + 'endpoints/markdown' + save_name + '.md'  # assumes the pattern starts with / after the replaces above
    save_dir_md = os.path.dirname(save_full_md)

    save_full_html = args.dest + os.sep + 'endpoints/html' + save_name + '.html'  # assumes the pattern starts with / after the replaces above
    save_dir_html = os.path.dirname(save_full_html)

    # create dirs without race:
    try:
        os.makedirs(save_dir_md)
        if args.generate_html:
            os.makedirs(save_dir_html)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full_md, 'w') as the_file:
        the_file.write(markdown)

    # use grip to render the .md to .html
    if args.generate_html:
        # https://github.com/joeyespo/grip
        # Transform GitHub-flavored Markdown to HTML
        from grip import export
        export(path=save_full_md, gfm=True, out_filename=save_full_html, username=args.github_user, password=args.github_password)


# write the endpoints toc
# TODO: sort endpoints by URI
toc_name = args.dest + os.sep + 'endpoints/markdown/toc.md'
try:
    os.remove(toc_name)
except:
    pass

with open(toc_name, 'w') as the_file:
    the_file.write(endpoints_result['markdown'].encode('utf8'))

# write the schemas 
# for schema in sorted(schemas):
all_schemas = a_node.schemas()['schemas']
for schema in all_schemas:
    save_name = schema['name']

    if 'void' == save_name: 
        continue;

    save_full_md = args.dest + os.sep + 'schemas/markdown/' + save_name + '.md'
    save_dir_md = os.path.dirname(save_full_md)

    save_full_html = args.dest + os.sep + 'schemas/html/' + save_name + '.html'
    save_dir_html = os.path.dirname(save_full_html)

    # create dirs without race:
    try:
        os.makedirs(save_dir_md)
        if args.generate_html:
            os.makedirs(save_dir_html)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full_md, 'w') as the_file:
        the_file.write(schema['markdown'].encode('utf8'))

    # use grip to render the .md to .html
    if args.generate_html:
        # https://github.com/joeyespo/grip
        # Transform GitHub-flavored Markdown to HTML
        from grip import export
        export(path=save_full_md, gfm=True, out_filename=save_full_html, username=args.github_user, password=args.github_password)
