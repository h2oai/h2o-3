package hex.genmodel.algos.deeplearning;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;


public class DeepLearningMojoModelTest {

    
    public static DeeplearningMojoModel mojo;

    private final double[][] rows = {
            {65,1,2,1,1.4,0,6},
            {72,1,3,2,6.7,0,7},
            {76,2,2,1,51.2,20,7},
            {61,2,4,2,66.7,27.2,7},
            {68,2,1,2,13,0,6},
            {68,2,4,2,4,0,7},
            {72,1,2,2,21.2,0,7},
            {73,1,2,1,2.6,0,5},
            {75,2,1,1,2.5,0,5},
            {58,1,2,1,3.1,0,7}
    };
    private final double[][] predictions = {
            {0,0.873172336831775,0.12682766316822494},
            {0,0.6743287110723505,0.3256712889276496},
            {1,0.28246387340148005,0.71753612659852},
            {0,0.7969635786866691,0.20303642131333088},
            {0,0.9679481355617076,0.03205186443829243},
            {1,0.12047098072198913,0.8795290192780109},
            {1,0.02230106808676718,0.9776989319132328},
            {0,0.8251272993149434,0.17487270068505656},
            {0,0.9972064227999036,0.00279357720009635},
            {1,0.4089861587081549,0.5910138412918451},
    };

    @Before
    public void setup() throws Exception {
        mojo = (DeeplearningMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);
    }

    @Test
    public void testScore0() throws Exception {
        for(int i = 0; i < rows.length; i++){
            double[] tmpPrediction = mojo.score0(rows[i], new double[3]);
            assertArrayEquals(predictions[i], tmpPrediction, 1e-5);
        }
    }


    @Test
    public void testPredict() throws Exception {
        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo);
        
        for(int i = 0; i < rows.length; i++) {
            final double[] row = rows[i];
            final double[] prediction = predictions[i];
            BinomialModelPrediction tmpPrediction = (BinomialModelPrediction) wrapper.predict(new RowData() {{
                put("AGE",      row[0]);
                put("RACE",     row[1]);
                put("DPROS",    row[2]);
                put("DCAPS",    row[3]);
                put("PSA",      row[4]);
                put("VOL",      row[5]);
                put("GLEASON",  row[6]);
            }});

            assertEquals((int) prediction[0], tmpPrediction.labelIndex);
            assertEquals(Integer.toString((int) prediction[0]), tmpPrediction.label);
            assertArrayEquals(new double[]{prediction[1], prediction[2]}, tmpPrediction.classProbabilities, 1e-5);
        }
    }
    
    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            InputStream is = DeepLearningMojoModelTest.class.getResourceAsStream(filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = DeepLearningMojoModelTest.class.getResourceAsStream(filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }
    
    @Test
    /**
     * Test the DeeplerningMojoModel is thread-safe.
     */
    public void testMultiThredMojoUsage(){
        // test ten times - it is not failed every time
        for(int j = 0; j < 10; j++) {
            ArrayList<MojoThread> threads = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                threads.add(new MojoThread(mojo, Integer.toString(i), rows, predictions));
            }
            for (MojoThread thread : threads) {
                thread.join();
            }
            boolean throwAssert = false;
            for (MojoThread thread : threads) {
                if (thread.isAssertThrew()) {
                    System.out.println("Thread " + thread.id + " was interrupted.");
                    throwAssert = true;
                }
            }
            assert !throwAssert : "Run "+j+" failed.";
        }
    }
    /**
     * Helper class to simulate multi thread usage of MOJO
     */
    class MojoThread implements Runnable {

        private final double[][] rows;
        private final double[][] predictions;

        MojoModel mojo;
        Thread thread;
        String id;
        boolean isAssertThrew;

        public MojoThread(MojoModel mojo, String id, double[][] rows, double[][] predictions){
            this.mojo = mojo;
            this.id = id;
            this.rows = rows;
            this.predictions = predictions;
            thread = new Thread(this, id);
            thread.start();
        }

        @Override
        public void run() {
            try {
                System.out.println("Thread: "+ id +" is starting.");
                for (int i = 0; i < rows.length; i++) {
                    double[] tmpPrediction = mojo.score0(rows[i], new double[3]);
                    try {
                        assertArrayEquals(predictions[i], tmpPrediction, 1e-1);
                        System.out.println("Thread: "+ id +" row index: "+i+" expected pred: " + predictions[i][1] + " actual pred: "+tmpPrediction[1]+".");
                    } catch (AssertionError ex){
                        System.err.println("Thread: "+ id +" row index: "+i+" expected pred: " + predictions[i][1] + " actual pred: "+tmpPrediction[1]+".");
                        isAssertThrew = true;
                    }
                    Thread.sleep(new Random().nextInt(10));
                }
            }
            catch (InterruptedException ex){
                System.out.println("Thread: "+ id +" interrupted.");
            }
            System.out.println("Thread: "+ id +" ends.");
        }

        public void join(){
            try {
                thread.join();
            } catch (InterruptedException ex){
                System.out.println("Thread: "+ id +" interrupted.");
            }
        }

        public boolean isAssertThrew(){
            return isAssertThrew;
        }
    }
}
