package water.network.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

class StreamGobbler implements Runnable {

    private final InputStream _is;

    StreamGobbler(InputStream is) {
        _is = is;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(_is)).lines()
                .forEach(System.out::println);
    }
}
