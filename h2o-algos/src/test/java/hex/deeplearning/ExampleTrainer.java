package hex.deeplearning;

import java.nio.FloatBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.bytedeco.javacpp.tensorflow.*;

public class ExampleTrainer {

    static class Options {
        int num_concurrent_sessions = 10; // The number of concurrent sessions
        int num_concurrent_steps = 10;    // The number of concurrent steps
        int num_iterations = 100;         // Each step repeats this many times
        boolean use_gpu = false;          // Whether to use gpu in the training
    }

    static Options ParseCommandLineFlags(String[] args) throws Exception {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i], value;
            if (arg.startsWith("--")) {
                arg = arg.substring(2);
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1);
            } else {
                continue;
            }
            int j = arg.indexOf('=');
            if (j < 0) {
                value = args[++i];
            } else {
                value = arg.substring(j + 1);
                arg = arg.substring(0, j);
            }
            try {
                java.lang.reflect.Field field = Options.class.getField(arg);
                Class cls = field.getDeclaringClass();
                if (cls == String.class) {
                    field.set(options, value);
                } else if (cls == int.class) {
                    field.setInt(options, Integer.parseInt(value));
                } else if (cls == boolean.class) {
                    field.setBoolean(options, Boolean.parseBoolean(value));
                } else {
                    throw new Exception("Unsupported option type: " + cls);
                }
            } catch (NoSuchFieldException e) {
                throw new Exception("Unknown command line flag: " + arg);
            } catch (NumberFormatException e) {
                throw new Exception("Error parsing command line flag: " + value);
            }
        }
        return options;
    }

    // A = [3 2; -1 0]; x = rand(2, 1);
    // We want to compute the largest eigenvalue for A.
    // repeat x = y / y.norm(); y = A * x; end
    static GraphDef CreateGraphDef() throws Exception {
        // TODO(jeff,opensource): This should really be a more interesting
        // computation.  Maybe turn this into an mnist model instead?
        GraphDefBuilder b = new GraphDefBuilder();

        // Store rows [3, 2] and [-1, 0] in row major format.
        Node a = Const(new float[] {3.f, 2.f, -1.f, 0.f}, new TensorShape(2, 2), b.opts());

        // x is from the feed.
        Node x = Const(new float[] {0.f}, new TensorShape(2, 1), b.opts().WithName("x"));

        // y = A * x
        Node y = MatMul(a, x, b.opts().WithName("y"));

        // y2 = y.^2
        Node y2 = Square(y, b.opts());

        // y2_sum = sum(y2)
        Node y2_sum = Sum(y2, Const(0, b.opts()), b.opts());

        // y_norm = sqrt(y2_sum)
        Node y_norm = Sqrt(y2_sum, b.opts());

        // y_normalized = y ./ y_norm
        Div(y, y_norm, b.opts().WithName("y_normalized"));

        GraphDef def = new GraphDef();
        Status s = b.ToGraphDef(def);
        if (!s.ok()) {
            throw new Exception(s.error_message().getString());
        }
        return def;
    }

    static String DebugString(Tensor x, Tensor y) {
        assert x.NumElements() == 2;
        assert y.NumElements() == 2;
        FloatBuffer x_flat = x.createBuffer();
        FloatBuffer y_flat = y.createBuffer();
        float lambda = y_flat.get(0) / x_flat.get(0);
        return String.format("lambda = %8.6f x = [%8.6f %8.6f] y = [%8.6f %8.6f]",
                lambda, x_flat.get(0), x_flat.get(1), y_flat.get(0), y_flat.get(1));
    }

    static void ConcurrentSteps(final Options opts, final int session_index) throws Exception {
        // Creates a session.
        SessionOptions options = new SessionOptions();
        final Session session = new Session(options);
        GraphDef def = CreateGraphDef();
        if (options.target() == null) {
            SetDefaultDevice(opts.use_gpu ? "/gpu:0" : "/cpu:0", def);
        }

        Status s = session.Create(def);
        if (!s.ok()) {
            throw new Exception(s.error_message().getString());
        }

        // Spawn M threads for M concurrent steps.
        int M = opts.num_concurrent_steps;
        ExecutorService step_threads = Executors.newFixedThreadPool(M);

        for (int step = 0; step < M; step++) {
            final int m = step;
            step_threads.submit(new Callable<Void>() { public Void call() throws Exception {
                // Randomly initialize the input.
                Tensor x = new Tensor(DT_FLOAT, new TensorShape(2, 1));
                FloatBuffer x_flat = x.createBuffer();
                x_flat.put(0, (float)Math.random());
                x_flat.put(1, (float)Math.random());

                // Iterations.
                TensorVector outputs = new TensorVector();
                for (int iter = 0; iter < opts.num_iterations; iter++) {
                    outputs.resize(0);
                    Status s = session.Run(new StringTensorPairVector(new String[] {"x"}, new Tensor[] {x}),
                            new StringVector("y:0", "y_normalized:0"), new StringVector(), outputs);
                    if (!s.ok()) {
                        throw new Exception(s.error_message().getString());
                    }
                    assert outputs.size() == 2;

                    Tensor y = outputs.get(0);
                    Tensor y_norm = outputs.get(1);
                    // Print out lambda, x, and y.
                    System.out.printf("%06d/%06d %s\n", session_index, m, DebugString(x, y));
                    // Copies y_normalized to x.
                    x.put(y_norm);
                }
                return null;
            }});
        }

        s = session.Close();
        if (!s.ok()) {
            throw new Exception(s.error_message().getString());
        }
        step_threads.shutdown();
    }

    static void ConcurrentSessions(final Options opts) throws Exception {
        // Spawn N threads for N concurrent sessions.
        int N = opts.num_concurrent_sessions;
        ExecutorService session_threads = Executors.newFixedThreadPool(N);
        for (int i = 0; i < N; i++) {
            final int n = i;
            session_threads.submit(new Callable<Void>() { public Void call() throws Exception {
                ConcurrentSteps(opts, n);
                return null;
            }});
        }
        session_threads.shutdown();
    }

    public static void main(String args[]) throws Exception {
        Options opts = ParseCommandLineFlags(args);
        InitMain("trainer", (int[])null, null);
        ConcurrentSessions(opts);
    }
}
