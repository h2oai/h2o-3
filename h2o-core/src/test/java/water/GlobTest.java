package water;


import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import water.persist.PersistManager;
import java.io.IOException;

public class GlobTest extends TestUtil {

    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void run() throws IOException {

        String[] pattern1 = PersistManager.getGlob("/Users/h2o/Desktop/[!A]/iris_*");
        System.out.println(pattern1[0].concat(pattern1[1]));
        Assert.assertEquals("/Users/h2o/Desktop/[^A]/iris_.*",(pattern1[0].concat(pattern1[1])));

        String[] pattern2 = PersistManager.getGlob("/Users/h2o/Desktop/[*A]/iris_*");
        System.out.println(pattern2[0].concat(pattern2[1]));
        Assert.assertEquals("/Users/h2o/Desktop/[.*A]/iris_.*",pattern2[0].concat(pattern2[1]));

        String[] pattern3 = PersistManager.getGlob("/Users/h2o/Desktop/[!abc]/iris_*_content_*");
        System.out.println(pattern3[0].concat(pattern3[1]));
        Assert.assertEquals("/Users/h2o/Desktop/[^abc]/iris_.*_content_.*",pattern3[0].concat(pattern3[1]));

        String[] pattern4 = PersistManager.getGlob("/Users/h2o/Desktop/[!a-d]/iris_*_content_*");
        System.out.println(pattern4[0].concat(pattern4[1]));
        Assert.assertEquals("/Users/h2o/Desktop/[^a-d]/iris_.*_content_.*",pattern4[0].concat(pattern4[1]));

    }

}