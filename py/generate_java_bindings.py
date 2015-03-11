# TODO: ugh:
import sys, pprint, argparse, string, errno, sets

sys.path.extend(['.','py'])
import h2o, h2o_util
import os

# print "ARGV is:", sys.argv

here=os.path.dirname(os.path.realpath(__file__))

parser = argparse.ArgumentParser(
    description='Attach to an H2O instance and call its REST API to generate the Java REST API bindings docs and write them to the filesystem.',
)
parser.add_argument('--verbose', '-v', help='verbose output', action='store_true')
parser.add_argument('--usecloud', help='ip:port to attach to', default='')
parser.add_argument('--host', help='hostname to attach to', default='localhost')
parser.add_argument('--port', help='port to attach to', type=int, default=54321)
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/bindings/Java'))
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def cons_java_type(pojo_name, name, h2o_type, schema_name):
    if schema_name is None:
        simple_type = string.replace(h2o_type, '[]', '')
        idx = string.find(h2o_type, '[]')
        brackets = '' if idx is -1 else h2o_type[idx:]
    else:
        simple_type = string.replace(schema_name, '[]', '')
        idx = string.find(schema_name, '[]')
        brackets = '' if idx is -1 else schema_name[idx:]

    if simple_type == 'string':
        return string.capitalize(simple_type) + brackets
    if h2o_type.startswith('Key<'): # Key<Frame> is a schema of FrameKeyVx
        return 'String' + brackets
    if simple_type in ['int', 'float', 'double', 'long', 'boolean', 'byte', 'short']:
        return simple_type + brackets
    if schema_name is not None:
        return simple_type + 'Proxy' + brackets

    # TODO:
    if simple_type == 'enum':
        return simple_type

    # Polymorphic fields can either be a scalar, a Schema, or an array of either of these:
    if simple_type == 'Polymorphic':
        return 'Polymorphic'

    # IcedWrapper fields can either be a scalar or an array of either of scalars:
    if simple_type == 'IcedWrapper':
        return 'Polymorphic'

    raise Exception('Unexpectedly found a ' + simple_type + ' field: ' + name + ' in pojo: ' + pojo_name)


# TODO: find and generate Enums

def generate_pojo(schema, pojo_name):
    global args
    if args.verbose: print 'Generating POJO: ', pojo_name

    pojo = []
    pojo.append("package water.bindings.pojos;")
    pojo.append("")
    pojo.append("public class " + pojo_name + " extends Object {")

    for field in schema['fields']:
        help = field['help']
        type = field['type']
        name = field['name']
        schema_name = field['schema_name']

        if name == '__meta': continue

        if type == 'Map': continue  # TODO
        if type == 'Iced': continue  # TODO

        java_type = cons_java_type(pojo_name, name, type, schema_name)
        
        if java_type == 'enum': continue  # TODO

        pojo.append("    /** {help} */".format(help=help))
        pojo.append("    public {type} {name};".format(type=java_type, name=name))
        pojo.append("")

    pojo.append("}")
    return pojo
    

######
# MAIN:
######
if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

endpoints_result = a_node.endpoints()
endpoints = endpoints_result['routes']

print 'creating the endpoint bindings in {}. . .'.format(args.dest)
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
    
    # TODO!


# write the schemas' POJOs
all_schemas = a_node.schemas()['schemas']
for schema in all_schemas:
    if 'void' == schema['name']: 
        continue;

    schema_name = schema['name']
    pojo_name = schema_name + 'Proxy';

    save_full = args.dest + os.sep + 'water/bindings/pojos/' + pojo_name + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in generate_pojo(schema, pojo_name):
            the_file.write("%s\n" % line)

