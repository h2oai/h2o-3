package water;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.io.InputStreamReader;

import java.util.TimerTask;
import java.util.Timer;

public class SignalTest extends TestUtil {
    int processID = 0;

    @Test
    public void intTest() throws IOException {

        String[] terms = {"SIGINT", "SIGTERM", "SIGHUP", "SIGABRT"};
        Timer t = new Timer();


        for (int i = 0; i < 4; i++) {


            String dir = System.getProperty(("user.dir"));


            //TODO fix path
            final Process q = Runtime.getRuntime().exec("java -cp build/*:build/test-results/* water.H2OApp");
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    q.destroy();
                }


            }));

            InputStream os = q.getInputStream();


            InputStream eos = q.getErrorStream();


            if (os.available() > 0) {
                //this prints out error stream
                InputStreamReader is = new InputStreamReader(eos);
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(is);

                String read = br.readLine();//

                while (read != null) {
                    sb.append(read);
                    read = br.readLine();
                }

            }

            if (q.getClass().getName().equals("java.lang.UNIXProcess")) {
                try {
                    Field a = q.getClass().getDeclaredField("pid");
                    a.setAccessible(true);
                    processID = a.getInt(q);


                } catch (Throwable e) {

                }

            }

            StringBuilder sb = new StringBuilder();
            //this prints output stream


            InputStreamReader iso = new InputStreamReader(os);
            StringBuilder sbo = new StringBuilder();
            BufferedReader bro = new BufferedReader(iso);

            String reado = bro.readLine();


            MTimerTask task = new MTimerTask(terms[i], processID);
            t.schedule(task, 1000);


            try {
                q.waitFor();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //t.cancel();
            }
        }
    }


    static class MTimerTask extends TimerTask {
        String task;
        int pid;

        public MTimerTask(String taskName, int pidA) {
            task = taskName;
            pid = pidA;

        }

        @Override
        public void run() {

            try {
                Runtime.getRuntime().exec("kill -" + task + " " + pid);
                //System.out.println("Closed with" + task);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }
}
