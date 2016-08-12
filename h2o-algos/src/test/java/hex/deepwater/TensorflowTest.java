/*


 */
package hex.deepwater;

        import org.apache.commons.io.IOUtils;
        import org.apache.commons.math3.distribution.IntegerDistribution;
        import org.junit.BeforeClass;
        import org.junit.Test;
        import water.TestUtil;
        import water.util.Pair;

        import javax.imageio.ImageIO;
        import java.awt.image.BufferedImage;
        import java.io.*;
        import java.nio.ByteBuffer;
        import java.nio.FloatBuffer;
        import java.nio.IntBuffer;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.Paths;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;

        import static org.bytedeco.javacpp.tensorflow.*;

public class TensorflowTest extends TestUtil {
    @BeforeClass
    public static void stall() { stall_till_cloudsize(1); }

    static String expandPath(String path) {
        return path.replaceFirst("^~", System.getProperty("user.home"));
    }

    Status loadImage(ImageParams imageParams, TensorVector outputs) throws Exception{
        String image_reader_name = "image_reader";
        String output_name = "normalize";

        Node image_reader;
        GraphDefBuilder graphBuilder = new GraphDefBuilder();
        Node file_reader = ReadFile(Const(imageParams.getImagePath(), graphBuilder.opts()), graphBuilder.opts().WithName(image_reader_name));

        if (imageParams.getImagePath().endsWith(".png")){
            image_reader = DecodePng(file_reader, graphBuilder.opts().WithAttr("channels", 3).WithName("png_reader"));
        } else {
            image_reader = DecodeJpeg(file_reader, graphBuilder.opts().WithAttr("channels", 3).WithName("jpeg_reader"));
        }
        // cast to float
        Node float_caster = Cast(image_reader, DT_FLOAT, graphBuilder.opts().WithName("float_caster"));

        Node dims_expander = ExpandDims(float_caster, Const(0, graphBuilder.opts()), graphBuilder.opts());
        Node resized = ResizeBilinear(dims_expander, Const(new int[]{imageParams.getInput_height(), imageParams.getInput_width()}, graphBuilder.opts().WithName("size")),graphBuilder.opts());
        Node normalized = Div(
                Sub(resized, Const(imageParams.getInput_mean(), graphBuilder.opts()), graphBuilder.opts().WithName("subtraction")),
                Const(imageParams.getInput_std(), graphBuilder.opts()),
                graphBuilder.opts());
        Squeeze(normalized, graphBuilder.opts());
        Reshape(normalized, Const(new int[]{-1, 784}, graphBuilder.opts()), graphBuilder.opts().WithName(output_name));

        GraphDef graph = new GraphDef();
        Status status = graphBuilder.ToGraphDef(graph);

        checkStatus(status);

        Session session = new Session(new SessionOptions());
        status = session.Create(graph);
        checkStatus(status);

        status = session.Run(new StringTensorPairVector(),
                new StringVector(output_name), new StringVector(), outputs);
        checkStatus(status);
        return Status.OK();
    }

    TensorVector getTopKLabels(Tensor distribution, int k){

        final String output_name = "top_k";

        GraphDefBuilder b = new GraphDefBuilder();

        TopKV2(Const(distribution, b.opts()), Const(k, b.opts()), b.opts().WithName("top_k"));

        GraphDef graph = new GraphDef();
        Status status = b.ToGraphDef(graph);

        checkStatus(status);
        Session session = new Session(new SessionOptions());
        checkStatus(session.Create(graph));
        TensorVector outputs = new TensorVector();
        status = session.Run( new StringTensorPairVector(),
                new StringVector( new String[]{output_name+ ":0", output_name+":1"}),
                new StringVector(),
            outputs);

        checkStatus(status);
        return outputs;
    }

    void checkStatus(Status status) {

        if (!status.ok()){
            throw new InternalError(status.error_message().getString());
        }
    }

    @Test
    public void inferMNIST() throws Exception {

        SessionOptions opt = new SessionOptions();
        Session sess = new Session(opt);
        GraphDef graph_def = new GraphDef();
        Status status = ReadBinaryProto(Env.Default(), expandPath("~/workspace/deepwater/tensorflow/models/mnist/mnist_with_summaries.pb"), graph_def);
        if (!status.ok()) {
            throw new InternalError("could not load serialized protocol buffer");
        }
        status = sess.Create(graph_def);
        if (!status.ok()) {
            throw new InternalError("could not create graph definition");
        }


        for (int i = 0; i < graph_def.node_size(); i++) {
            System.out.println(">>>>> " + graph_def.node(i).name().getString());
        }

        MNISTImageDataset dataset = new MNISTImageDataset("/tmp/data/t10k-labels-idx1-ubyte.gz", "/tmp/data/t10k-images-idx3-ubyte.gz");
        List<Pair<Integer, float[]>> images = dataset.loadDigitImages();

        int wrong_prediction = 0;
        int right_prediction = 0;
        int batch_size = 10;
        float[] batch = new float[784*batch_size];
        int[] batch_labels = new int[784*batch_size];
        int current_batch_size = 0;
        for( Pair<Integer, float[]> sample: images ){
            float[] array = sample.getValue();
            // accumulate images and labels into a batch
            System.arraycopy(array, 0, batch, current_batch_size*784, array.length );
            batch_labels[current_batch_size] = sample.getKey();
            current_batch_size++;
            if (current_batch_size < batch_size) {
               continue;
            }

            current_batch_size = 0;
            int[] predictions = inferDigit(sess, batch, batch_size);
            for (int i = 0; i < predictions.length; i++) {
                if (predictions[i] != batch_labels[i]) {
                    wrong_prediction++;
                } else {
                    right_prediction++;
                }
            }
        }

        System.out.println("right predictions: " + right_prediction + " - wrong Predictions:" + wrong_prediction);
        System.out.println("accuracy:" + right_prediction/1.0*(right_prediction+wrong_prediction));
    }

    int[] inferDigit(Session sess, float[] data, int batch_size) throws Exception {
            TensorVector outputs = new TensorVector();
            ImageParams params = new ImageParams(expandPath("~/workspace/mnist_png/mnist_png/testing/0/1416.png"), 28, 28, 128, 128);
            loadImage(params, outputs); // "input/x-input", "layer2/activation", outputs);

            Tensor result = outputs.get(0);

            outputs = new TensorVector();

            Tensor mnist_batch_image = new Tensor(DT_FLOAT, new TensorShape(batch_size, 784));

            ((FloatBuffer)mnist_batch_image.createBuffer()).put(data);
            Tensor dropout = new Tensor( DT_FLOAT, new TensorShape(1));

            FloatBuffer  dropout_b = dropout.createBuffer();
            dropout_b.put(1.0f);

            Status status = sess.Run(new StringTensorPairVector(new String[]{"input/x-input", "dropout/Placeholder"},
                        new Tensor[]{ mnist_batch_image, dropout }),
                    new StringVector("layer2/activation"),
                    new StringVector(), outputs);
            checkStatus(status);
            getTopKLabels(outputs.get(0), 1);

            FloatBuffer fb = outputs.get(0).createBuffer();
            float[] activation_layer = new float[batch_size];
            fb.get(activation_layer);

            int[] indexes = new int[batch_size];
            ((IntBuffer) outputs.get(1).createBuffer()).get(indexes);

            for (int i = 0; i < activation_layer.length ; i++) {
                //System.out.println("activation layer:" + indexes[i] + ":" + activation_layer[i] );
            }

            return indexes;

    }

    public Tensor asTensor(String value) {
        Tensor t = new Tensor( DT_STRING, new TensorShape(value.length() ));
        t.createStringArray().put(value);
        return t;
    }

    @Test
    public void inferInception() throws Exception {
        SessionOptions opt = new SessionOptions();
        Session sess = new Session(opt);
        GraphDef graph_def = new GraphDef();
        Status status = ReadBinaryProto(Env.Default(), expandPath("~/workspace/deepwater/tensorflow/models/inception/classify_image_graph_def.pb"), graph_def);
        checkStatus(status);
        status = sess.Create(graph_def);
        checkStatus(status);

        HashMap<Integer, String>  uid_to_human_name = loadMapping(expandPath("~/workspace/deepwater/tensorflow/models/inception/imagenet_2012_challenge_label_map_proto.pbtxt"),
                expandPath("~/workspace/deepwater/tensorflow/models/inception/imagenet_synset_to_human_label_map.txt"));


        String image_path = expandPath("~/workspace/deepwater/tensorflow/models/inception/cropped_panda.jpg");
        BufferedImage bimg = ImageIO.read(new File(image_path));

        byte[] img = Files.readAllBytes(Paths.get(image_path));
        TensorVector outputs = new TensorVector();
        Tensor image_data = new Tensor(DT_STRING, new TensorShape(img.length));
        StringArray buffer_image_data = image_data.createStringArray();
        //buffer_image_data.put(new StringArray().data().put(img));
        buffer_image_data.resize(img.length);
        buffer_image_data.data().put(img);
        assert buffer_image_data.size()  == img.length: image_data.dim_size(0) + " " + img.length;

        Tensor image_path_t = asTensor(new String(img));
        status = sess.Run(new StringTensorPairVector(new String[]{"DecodeJpeg/contents"}, new Tensor[]{image_data}),
                new StringVector("softmax"), new StringVector(), outputs );
        checkStatus(status);

        FloatBuffer softmax = outputs.get(0).createBuffer();
        TensorVector results = getTopKLabels(outputs.get(0), 5);

        FloatBuffer topK = results.get(0).createBuffer();
        IntBuffer topKindex = results.get(1).createBuffer();
        assert topK.limit() == topKindex.limit();
        for (int i = 0; i < topK.limit(); i++) {
            System.out.println(i + " " + topK.get(i) + " " + uid_to_human_name.get(topKindex.get(i)));
        }
    }

    HashMap<Integer, String> loadMapping(String label_map_proto, String label_map){
        Pattern regexp = Pattern.compile("^n(\\d+)(\\s+)([ \\S,]*)");
        Matcher matcher = regexp.matcher("");
        HashMap<Integer, String> uid_to_class_name = new HashMap<>();
        try {
            // Read the uid -> Class mapping
            BufferedReader buff_reader = new BufferedReader( new FileReader(label_map));
            LineNumberReader reader = new LineNumberReader(buff_reader);
            String line;
            while ((line = reader.readLine()) != null) {
                matcher.reset(line);
                if(matcher.find()){
                    uid_to_class_name.put(Integer.parseInt(matcher.group(1)), matcher.group(3));
                }
            }
        } catch(IOException e){
            e.printStackTrace();
        }

        HashMap<Integer, String> result = new HashMap<>();
        regexp = Pattern.compile("^.*?(\\d+).*");
        matcher = regexp.matcher("");

        try {
            // Read the uid -> int mapping
            BufferedReader buff_reader = new BufferedReader( new FileReader(label_map_proto));
            LineNumberReader reader = new LineNumberReader(buff_reader);
            List<Integer> values = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // skip comments. This is needed or the regex will pick up the number inside it.
                if (line.startsWith("#")){
                    continue;
                }
                matcher.reset(line);
                if(matcher.find()){
                    values.add(Integer.parseInt(matcher.group(1)));
                }
            }
            assert values.size() % 2 == 0: values;

            // consume the array as a sequence of [(uid,id),...]
            for (int i = 0; i < values.size(); i+=2) {
                int index = values.get(i);
                int uid = values.get(i+1);
                // remap uid to id
                result.put(index, uid_to_class_name.get(uid));
            }

        } catch(IOException e){
            e.printStackTrace();
        }

        return result;
    }

    private static class ImageParams {
        private final String imagePath;
        private final int input_height;
        private final int input_width;
        private final float input_mean;
        private final float input_std;

        private ImageParams(String imagePath, int input_height, int input_width, float input_mean, float input_std) {
            this.imagePath = imagePath;
            this.input_height = input_height;
            this.input_width = input_width;
            this.input_mean = input_mean;
            this.input_std = input_std;
        }

        public String getImagePath() {
            return imagePath;
        }

        public int getInput_height() {
            return input_height;
        }

        public int getInput_width() {
            return input_width;
        }

        public float getInput_mean() {
            return input_mean;
        }

        public float getInput_std() {
            return input_std;
        }
    }
}