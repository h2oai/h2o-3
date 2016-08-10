/*


 */
package hex.deepwater;

        import hex.ModelMetricsMultinomial;
        import org.bytedeco.javacpp.indexer.FloatIndexer;
        import org.bytedeco.javacpp.tensorflow;
        import org.junit.BeforeClass;
        import org.junit.Test;
        import water.Futures;
        import water.TestUtil;
        import water.fvec.Frame;
        import water.fvec.Vec;
        import water.gpu.ImageTrain;
        import water.gpu.util;
        import water.util.RandomUtils;

        import java.io.*;
        import java.nio.FloatBuffer;
        import java.nio.IntBuffer;
        import java.util.ArrayList;
        import java.util.Collections;
        import java.util.Random;

import static org.bytedeco.javacpp.tensorflow.*;

public class TensorflowTest extends TestUtil {
    @BeforeClass
    public static void stall() { stall_till_cloudsize(1); }

    final boolean GPU = System.getenv("CUDA_PATH")!=null;

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

    void getTopKLabels(TensorVector outputs, int k){

        final String output_name = "top_k";

        GraphDefBuilder b = new GraphDefBuilder();

        TopKV2(Const(outputs.get(0), b.opts()), Const(k, b.opts()), b.opts().WithName("top_k"));

        GraphDef graph = new GraphDef();
        Status status = b.ToGraphDef(graph);

        checkStatus(status);

        Session session = new Session(new SessionOptions());
        checkStatus(session.Create(graph));
        status = session.Run( new StringTensorPairVector(),
                new StringVector( new String[]{output_name+ ":0", output_name+":1"}),
                new StringVector(),
            outputs);

        checkStatus(status);

    }

    void checkStatus(Status status) {

        if (!status.ok()){
            throw new InternalError(status.error_message().getString());
        }
    }

    @Test
    public void inferMNIST() throws IOException {

        SessionOptions opt = new SessionOptions();
        Session sess = new Session(opt);
        GraphDef graph_def = new GraphDef();
        Status status = ReadBinaryProto(Env.Default(), expandPath("~/workspace/deepwater/tensorflow/models/mnist/mnist_with_summaries.pb"), graph_def);
        if (!status.ok()){
           throw new InternalError("could not load serialized protocol buffer");
        }
        status = sess.Create(graph_def);
        if (!status.ok()){
            throw new InternalError("could not create graph definition");
        }


        for (int i = 0; i < graph_def.node_size(); i++) {
          System.out.println(">>>>> "+  graph_def.node(i).name().getString());
        }

        try {
            TensorVector outputs = new TensorVector();
            ImageParams params = new ImageParams(expandPath("~/workspace/mnist_png/mnist_png/testing/0/1416.png"), 28, 28, 128, 128);
            loadImage(params, outputs); // "input/x-input", "layer2/activation", outputs);

            Tensor result = outputs.get(0);

            outputs = new TensorVector();

            Tensor dropout = new Tensor( DT_FLOAT, new TensorShape(1));

            FloatBuffer  dropout_b = dropout.createBuffer();
            dropout_b.put(1.0f);

            status = sess.Run(new StringTensorPairVector(new String[]{"input/x-input", "dropout/Placeholder"},
                        new Tensor[]{ result, dropout }),
                    new StringVector("layer2/activation"),
                    new StringVector(), outputs);
            checkStatus(status);
            getTopKLabels(outputs, 5);

            FloatBuffer fb = outputs.get(0).createBuffer();
            float[] activation_layer = new float[10];
            fb.get(activation_layer);

            int[] indexes = new int[10];
            ((IntBuffer) outputs.get(1).createBuffer()).get(indexes);

            for (int i = 0; i < activation_layer.length ; i++) {
                System.out.println("activation layer:" + indexes[i] + ":" + activation_layer[i] );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


/*        // load the cuda lib in CUDA_PATH, optional. theoretically we can find them if they are in LD_LIBRARY_PATH
        if (GPU) util.loadCudaLib();
        util.loadNativeLib("mxnet");
        util.loadNativeLib("Native");


        BufferedImage img = ImageIO.read(new File(expandPath("~/deepwater/test/test2.jpg")));

        int w = 224, h = 224;

        BufferedImage scaledImg = new BufferedImage(w, h, img.getType());

        Graphics2D g2d = scaledImg.createGraphics();
        g2d.drawImage(img, 0, 0, w, h, null);
        g2d.dispose();

        float[] pixels = new float[w * h * 3];

        int r_idx = 0;
        int g_idx = r_idx + w * h;
        int b_idx = g_idx + w * h;

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                Color mycolor = new Color(scaledImg.getRGB(j, i));
                int red = mycolor.getRed();
                int green = mycolor.getGreen();
                int blue = mycolor.getBlue();
                pixels[r_idx] = red; r_idx++;
                pixels[g_idx] = green; g_idx++;
                pixels[b_idx] = blue; b_idx++;
            }
        }

        ImagePred m = new ImagePred();

        // the path to Inception model
        m.setModelPath(expandPath("~/deepwater/Inception"));

        m.loadInception();

        System.out.println("\n\n" + m.predict(pixels)+"\n\n");*/
    }

    @Test
    public void inceptionFineTuning() throws IOException {
        if (GPU) util.loadCudaLib();
        util.loadNativeLib("mxnet");
        util.loadNativeLib("Native");

        String path = expandPath("~/kaggle/statefarm/input/");
        BufferedReader br = new BufferedReader(new FileReader(new File(path+"driver_imgs_list.csv")));

        ArrayList<Float> train_labels = new ArrayList<>();
        ArrayList<String> train_data = new ArrayList<>();

        String line;
        br.readLine(); //skip header
        while ((line = br.readLine()) != null) {
            String[] tmp = line.split(",");
            train_labels.add(new Float(tmp[1].substring(1)).floatValue());
            train_data.add(path+"train/"+tmp[1]+"/"+tmp[2]);
        }
        br.close();

        int batch_size = 64;
        int classes = 10;

        ImageTrain m = new ImageTrain();
        m.buildNet(classes, batch_size, "inception_bn");
        m.loadParam(expandPath("~/deepwater/Inception/model.params"));

        int max_iter = 6; //epochs
        int count = 0;
        for (int iter = 0; iter < max_iter; iter++) {
            m.setLR(3e-3f/(1+iter));
            //each iteration does a different random shuffle
            Random rng = RandomUtils.getRNG(0);
            rng.setSeed(0xDECAF+0xD00D*iter);
            Collections.shuffle(train_labels,rng);
            rng.setSeed(0xDECAF+0xD00D*iter);
            Collections.shuffle(train_data,rng);

            DeepWaterImageIterator img_iter = new DeepWaterImageIterator(train_data, train_labels, batch_size, 224, 224, 3);
            Futures fs = new Futures();
            while(img_iter.Next(fs)){
                float[] data = img_iter.getData();
                float[] labels = img_iter.getLabel();
                float[] pred = m.train(data, labels);
                if (count++ % 10 != 0) continue;

                Vec[] classprobs = new Vec[classes];
                String[] names = new String[classes];
                for (int i=0;i<classes;++i) {
                    names[i] = "c" + i;
                    double[] vals=new double[batch_size];
                    for (int j = 0; j < batch_size; ++j) {
                        int idx=j*classes+i; //[p0,...,p9,p0,...,p9, ... ,p0,...,p9]
                        vals[j] = pred[idx];
                    }
                    classprobs[i] = Vec.makeVec(vals,Vec.newKey());
                }
                water.fvec.Frame preds = new Frame(names,classprobs);
                long[] lab = new long[batch_size];
                for (int i=0;i<batch_size;++i)
                    lab[i] = (long)labels[i];
                Vec actual = Vec.makeVec(lab,names,Vec.newKey());
                ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(preds,actual);
                System.out.println(mm.toString());
            }
            m.saveParam(path+"/param."+iter);
        }
        scoreTestSet(path,classes,m);
    }

    public static void scoreTestSet(String path, int classes, ImageTrain m) throws IOException {
        // make test set predictions
        BufferedReader br = new BufferedReader(new FileReader(new File(path+"test_list.csv"))); //file created with 'cut -d, -f1 sample_submission.csv | sed 1d > test_list.csv'

        ArrayList<Float> test_labels = new ArrayList<>();
        ArrayList<String> test_data = new ArrayList<>();

        String line;
        while ((line = br.readLine()) != null) {
            test_labels.add(new Float(-999)); //dummy
            test_data.add(path+"test/"+line);
        }

        br.close();

        FileWriter fw = new FileWriter(path+"/submission.csv");
        int batch_size = 64; //avoid issues with batching at the end of the test set
        DeepWaterImageIterator img_iter = new DeepWaterImageIterator(test_data, test_labels, batch_size, 224, 224, 3);
        fw.write("img,c0,c1,c2,c3,c4,c5,c6,c7,c8,c9\n");
        Futures fs = new Futures();
        while(img_iter.Next(fs)) {
            float[] data = img_iter.getData();
            String[] files = img_iter.getFiles();
            float[] pred = m.predict(data);
            for (int i=0;i<batch_size;++i) {
                String file = files[i];
                String[] pcs = file.split("/");
                fw.write(pcs[pcs.length-1]);
                for (int j=0;j<classes;++j) {
                    int idx=i*classes+j;
                    fw.write(","+pred[idx]);
                }
                fw.write("\n");
            }
        }
        fw.close();
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