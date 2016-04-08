import sys, pprint, argparse, errno, re, string

# TODO: ugh:
sys.path.insert(1, '../../py')
import h2o
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
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/src-gen/main/java'))
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def cons_java_type(pojo_name, name, h2o_type, schema_name):
    if schema_name is None or h2o_type.startswith('enum'):
        simple_type = h2o_type.replace('[]', '')
        idx = h2o_type.find('[]')
        brackets = '' if idx is -1 else h2o_type[idx:]
    else:
        simple_type = schema_name
        idx = h2o_type.find('[]')
        brackets = '' if idx is -1 else h2o_type[idx:]

    if h2o_type.startswith('Map'):
        java_type = h2o_type
        java_type = java_type.replace('<string,', '<String,')
        java_type = java_type.replace(',string>', ',String>')
        return java_type

    if simple_type == 'string':
        return simple_type.capitalize() + brackets
    # TODO: for input keys are String; for output they are KeyV3 and children
    if h2o_type.startswith('Key<'): # Key<Frame> is a schema of FrameKeyVx
#        return 'String' + brackets
        return schema_name + brackets
    if simple_type in ['int', 'float', 'double', 'long', 'boolean', 'byte', 'short']:
        return simple_type + brackets
    if simple_type == 'enum':
        return schema_name + brackets
    if schema_name is not None:
        return simple_type + brackets


    # Polymorphic fields can either be a scalar, a Schema, or an array of either of these:
    if simple_type == 'Polymorphic':
        return 'Object' # TODO: Polymorphic class?

    # IcedWrapper fields can either be a scalar or an array of either of scalars:
    if simple_type == 'IcedWrapper':
        return 'Object' # TODO: Polymorphic class?

    raise Exception('Unexpectedly found a ' + simple_type + ' field: ' + name + ' in pojo: ' + pojo_name)


# generate a Schema POJO and find any Enums it uses
def generate_pojo(schema, pojo_name):
    global args
    global enums

    if args.verbose: print('Generating POJO: ', pojo_name)

    pojo = []
    pojo.append("package water.bindings.pojos;")
    pojo.append("")

    has_map = False
    for field in schema['fields']:
        if field['type'].startswith('Map'):
            has_map = True

    pojo.append("import com.google.gson.Gson;")
    if has_map:
        pojo.append("import java.util.Map;")
        pojo.append("")

    superclass = schema['superclass']
    if 'Iced' == superclass:
        # top of the schema class hierarchy
        superclass = 'Object'

    pojo.append("public class " + pojo_name + " extends {superclass} ".format(superclass=superclass) + '{')

    first = True
    for field in schema['fields']:
        help = field['help']
        type = field['type']
        name = field['name']
        schema_name = field['schema_name']

        if name == '__meta': continue

        if type == 'Iced': continue  # TODO

        java_type = cons_java_type(pojo_name, name, type, schema_name)

        if type.startswith('enum'):
            enum_name = field['schema_name']
            if enum_name not in enums:
                # save it for later
                enums[enum_name] = field['values']

        if not first:
            pojo.append("")

        if field['is_inherited']:
            pojo.append("    /* INHERITED: {help} ".format(help=help))
            pojo.append("     * public {type} {name};".format(type=java_type, name=name))
            pojo.append("     */")
        else:
            pojo.append("    /** {help} */".format(help=help))
            pojo.append("    public {type} {name};".format(type=java_type, name=name))

        first = False

    pojo.append("")
    pojo.append("    /** Return the contents of this object as a JSON String. */")
    pojo.append("    @Override")

    pojo.append("    public String toString() {")
    pojo.append("        return new Gson().toJson(this);")
    pojo.append("    }")

    pojo.append("}")
    return pojo


def generate_enum(name, values):

    if args.verbose: print('Generating enum: ', name)

    pojo = []
    pojo.append("package water.bindings.pojos;")
    pojo.append("")
    pojo.append("public enum " + name + " {")

    for value in values:
        pojo.append("    {value},".format(value=value))

    pojo.append("}")
    return pojo

# NOTE: not complete yet
def generate_retrofit_proxies(endpoints_meta, all_schemas_map):
    '''
    Walk across all the endpoint metadata returning a map of classnames to interface definitions.
    Retrofit interfaces look like this:

    public interface GitHubService {
        @GET("/users/{user}/repos")
        Call<List<Repo>> listRepos(@Path("user") String user);
    }
    '''
    pojos = {}
    java_type_map = { 'string': 'String' }

    endpoints_by_entity = {}  # entity (e.g., Frames) maps to an array of endpoints

    # For each endpoint grab the endpoint prefix (the entity), e.g. ModelBuilders, for use as the classname:
    entity_pattern_str = r"/[0-9]+?/([^/]+)(/.*)?"  # Python raw string
    entity_pattern = re.compile(entity_pattern_str)

    # Collect the endpoints for each REST entity
    for meta in endpoints_meta:
        h2o.H2O.verboseprint('finding entity for url_pattern: ' + meta['url_pattern'])
        m = entity_pattern.match(meta['url_pattern'])
        entity = m.group(1)

        # If the route contains a suffix like .bin strip it off.
        if '.' in entity:
            entity = entity.split('.')[0]

        h2o.H2O.verboseprint('found entity: ' + entity)

        if entity not in endpoints_by_entity:
            endpoints_by_entity[entity] = []
        endpoints_by_entity[entity].append(meta)


    # replace path vars like (?<schemaname>.*) with {schemaname} for Retrofit's annotation
    # TODO: fails for /3/Metadata/endpoints/(?<num>[0-9]+)
    var_pattern_str = r"\(\?<(.+?)>\.\*\)"  # Python raw string
    var_pattern = re.compile(var_pattern_str)

    # Walk across all the entities and generate a class with methods for all its endpoints:
    for entity in endpoints_by_entity:
        pojo = []
        signatures = {}

        pojo.append("package water.bindings.proxies.retrofit;")
        pojo.append("")
        pojo.append("import water.bindings.pojos.*;")
        pojo.append("import retrofit2.*;")
        pojo.append("import retrofit2.http.*;")
        pojo.append("import java.util.Map;")
        pojo.append("")
        pojo.append("public interface " + entity + " {")

        first = True
        for meta in endpoints_by_entity[entity]:
            path = meta['url_pattern']

            # These redundant paths cause conflicts:
            if path == "/3/ModelMetrics/frames/(?<frame>.*)/models/(?<model>.*)" or \
               path == "/3/ModelMetrics/frames/(?<frame>.*)":
                continue
            
            path_parm_names = meta['path_params']

            # replace all the vars in the path with the actual field names from path_params
            retrofit_path = path
            idx = 0
            while re.search(var_pattern, retrofit_path):
                retrofit_path = var_pattern.sub(r'{' + path_parm_names[idx] + '}', retrofit_path, 1)
                idx += 1

            retrofit_path = retrofit_path.replace('\\', '\\\\')
            summary = meta['summary']
            http_method = meta['http_method']
            input_schema_name  = meta['input_schema']
            output_schema_name = meta['output_schema']

            handler_method = meta['handler_method']

            method = handler_method

            # NOTE: hackery due to the way the paths are formed: POST to /99/Grid/glm and to /3/Grid/deeplearning both call methods called train
            if (entity == 'Grid' or entity == 'ModelBuilders') and (method == 'train'):
                # /99/Grid/glm or /3/ModelBuilders/glm
                pieces = path.split('/')
                if len(pieces) != 4:
                    raise Exception("Expected 3 parts to this path (something like /99/Grid/glm): " + path)
                algo = pieces[3]
                method = method + '_' + algo  # train_glm()
            elif (entity == 'ModelBuilders') and (method == 'validate_parameters'):
                # /3/ModelBuilders/glm/parameters
                pieces = path.split('/')
                if len(pieces) != 5:
                    raise Exception("Expected 3 parts to this path (something like /3/ModelBuilders/glm/parameters): " + path)
                algo = pieces[3]
                method = method + '_' + algo  # validate_parameters_glm()

            # TODO: handle query parameters from RequestSchema
            parms = ""

            if http_method == 'POST':
                is_post = True
            else:
                is_post = False
            
            input_schema = all_schemas_map[input_schema_name]

            # calculate indent
            indent = ' ' * len('    Call<{output_schema_name}> {method}('.format(output_schema_name = output_schema_name, method = method))
            
            # include path parms first, and then POST body parms
            first_parm = True
            for parm in path_parm_names:
                # find the metadata for the field from the input schema:
                fields = [field for field in input_schema['fields'] if field['name'] == parm]
                if len(fields) != 1:
                    print('Failed to find parameter: ' + parm + ' for endpoint: ' + repr(meta))
                field = fields[0]
                if field['direction'] == 'OUTPUT': continue

                # cons up the proper Java type:
                parm_type = cons_java_type(entity, field['name'], field['type'], field['schema_name'])

                # Send keys as Strings
                # TODO: brackets
                if parm_type.endswith('KeyV3'):
                    parm_type = 'String'

                if not first_parm: parms += ',\n'; parms += indent
                parms += '@Path("{parm}") '.format(parm = parm)
                parms += parm_type
                parms += ' '
                parms += parm
                first_parm = False

            if is_post:
                for field in input_schema['fields']:
                    if field['direction'] == 'OUTPUT': continue
                    if field['name'] in path_parm_names: continue

                    # cons up the proper Java type:
                    parm_type = cons_java_type(entity, field['name'], field['type'], field['schema_name'])
                    parm = field['name']

                    # Send keys as Strings
                    # TODO: brackets
                    if parm_type.endswith('KeyV3'):
                        parm_type = 'String'

                    if not first_parm: parms += ',\n'; parms += indent
                    parms += '@Field("{parm}") '.format(parm = parm)
                    parms += parm_type
                    parms += ' '
                    parms += parm
                    first_parm = False

            # check for conflicts:
            signature = '{method}({parms});'.format(method = method, parms = parms)
            if signature in signatures:
                print('ERROR: found a duplicate method signature in entity ' + entity + ': ' + signature)
            else:
                signatures[signature] = signature

            if not first: pojo.append('')
            pojo.append('    /** {summary} */'.format(summary = summary))
            if http_method == 'POST':
                pojo.append('    @FormUrlEncoded')
            pojo.append('    @{http_method}("{path}")'.format(http_method = http_method, path = retrofit_path))
            pojo.append('    Call<{output_schema_name}> {method}({parms});'.format(output_schema_name = output_schema_name, method = method, parms = parms))

            first = False

        pojo.append("}")
        pojos[entity] = pojo

    return pojos


######
# MAIN:
######
if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

print('creating the Java bindings in {}. . .'.format(args.dest))


#################################################################
# Get all the schemas and generate POJOs or Enums as appropriate.
# Note the medium ugliness that the enums list is global. . .
#################################################################
enums = {}

# write the schemas' POJOs, discovering enums on the way
all_schemas = a_node.schemas()['schemas']
all_schemas_map = {}  # save for later use

for schema in all_schemas:
    if 'void' == schema['name']:
        continue;

    schema_name = schema['name']
    pojo_name = schema_name;

    all_schemas_map[schema_name] = schema

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

########################
# Generate Enum classes.
########################
for name, values in enums.items():
    pojo_name = name;

    save_full = args.dest + os.sep + 'water/bindings/pojos/' + pojo_name + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in generate_enum(name, values):
            the_file.write("%s\n" % line)

#########################################################################
# Get the list of endpoints and generate Retrofit proxy methods for them.
#########################################################################
endpoints_result = a_node.endpoints()
endpoints = endpoints_result['routes']

if h2o.H2O.verbose:
    print('Endpoints: ')
    pp.pprint(endpoints)

# Collect all the endpoints:
endpoints_meta = []
for num in range(len(endpoints)):
    meta = a_node.endpoint_by_number(num)['routes'][0]
    endpoints_meta.append(meta)

## Generate source code for a class for each entity (e.g., ModelBuilders):
retrofitProxies = generate_retrofit_proxies(endpoints_meta, all_schemas_map)

# TODO: makedirs only once!

# Write them out:
for entity, proxy in retrofitProxies.items():
    save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + entity + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in proxy:
            the_file.write("%s\n" % line)


#####################################################
# Write out an example program that uses the proxies.
#####################################################
retrofit_example = '''package water.bindings.proxies.retrofit;

import water.bindings.pojos.*;
import com.google.gson.*;
import retrofit2.*;
import retrofit2.http.*;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import java.io.IOException;
import java.lang.reflect.Type;

public class Example {

    /**
     * Keys get sent as Strings and returned as objects also containing the type and URL,
     * so they need a custom GSON serializer.
     */
    private static class KeySerializer implements JsonSerializer<KeyV3> {
        public JsonElement serialize(KeyV3 key, Type typeOfKey, JsonSerializationContext context) {
            return new JsonPrimitive(key.name);
        }
    }

    public static JobV3 poll(Retrofit retrofit, String job_id) {
        Jobs jobsService = retrofit.create(Jobs.class);
        Response<JobsV3> jobs_response = null;

        int retries = 3;
        JobsV3 jobs = null;
        do {
            try {
                jobs_response = jobsService.fetch(job_id).execute();
            }
            catch (IOException e) {
                System.err.println("Caught exception: " + e);
            }
            if (! jobs_response.isSuccessful())
                if (retries-- > 0) 
                   continue;
                else
                    throw new RuntimeException("/3/Jobs/{job_id} failed 3 times.");

            jobs = jobs_response.body();
            if (null == jobs.jobs || jobs.jobs.length != 1)
                throw new RuntimeException("Failed to find Job: " + job_id);
            if (! "RUNNING".equals(jobs.jobs[0].status)) try { Thread.sleep(100); } catch (InterruptedException e) {} // wait 100mS
        } while ("RUNNING".equals(jobs.jobs[0].status));
        return jobs.jobs[0];
    }

    public static void main (String[] args) {
        Gson gson = new GsonBuilder().registerTypeAdapter(KeyV3.class, new KeySerializer()).create();

        Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("http://localhost:54321/") // note trailing slash for Retrofit 2
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build();

        CreateFrame createFrameService = retrofit.create(CreateFrame.class);
        Frames framesService = retrofit.create(Frames.class);
        Models modelsService = retrofit.create(Models.class);

        try {
            // NOTE: the Call objects returned by the service can't be reused, but they can be cloned.
            Response<FramesV3> all_frames_response = framesService.list().execute();
            Response<ModelsV3> all_models_response = modelsService.list().execute();

            if (all_frames_response.isSuccessful()) {
                FramesV3 all_frames = all_frames_response.body();
                System.out.println("All Frames: ");
                System.out.println(all_frames);
            } else {
                System.err.println("framesService.list() failed");
            }
            if (all_models_response.isSuccessful()) {
                ModelsV3 all_models = all_models_response.body();
                System.out.println("All Models: ");
                System.out.println(all_models);
            } else {
                System.err.println("modelsService.list() failed");
            }

            Response<JobV3> create_frame_response = createFrameService.run(null, 1000, 100, 42, 42, true, 0, 100000, 0.2, 100, 0.2, 32767, 0.2, 0.5, 0.2, 0, 0.2, 2, true, null).execute();
            if (create_frame_response.isSuccessful()) {
                JobV3 job = create_frame_response.body();

                if (null == job || null == job.key)
                    throw new RuntimeException("CreateFrame returned a bad Job: " + job);

                job = poll(retrofit, job.key.name);

                KeyV3 new_frame = job.dest;
                System.out.println("Created frame: " + new_frame);

                all_frames_response = framesService.list().execute();
                if (all_frames_response.isSuccessful()) {
                    FramesV3 all_frames = all_frames_response.body();
                    System.out.println("All Frames (after createFrame): ");
                    System.out.println(all_frames);
                } else {
                    System.err.println("framesService.list() failed");
                }

                Response<FramesV3> one_frame_response = framesService.fetch(new_frame.name).execute();
                if (one_frame_response.isSuccessful()) {
                    FramesV3 one_frames = one_frame_response.body();
                    System.out.println("One Frame (after createFrame): ");
                    System.out.println(one_frames);
                } else {
                    System.err.println("framesService.fetch() failed");
                }

            } else {
                System.err.println("createFrameService.run() failed");
            }
        }
        catch (IOException e) {
            System.err.println("Caught exception: " + e);
        }
    }
}
'''

save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + 'Example' + '.java'
save_dir = os.path.dirname(save_full)

# create dirs without race:
try:
    os.makedirs(save_dir)
except OSError as exception:
    if exception.errno != errno.EEXIST:
        raise

with open(save_full, 'w') as the_file:
    the_file.write("%s\n" % retrofit_example)
