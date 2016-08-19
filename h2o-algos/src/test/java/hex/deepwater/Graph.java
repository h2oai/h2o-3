package hex.deepwater;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.TextFormat;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.OpDef;
import org.tensorflow.framework.OpList;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by fmilo on 8/17/16.
 */
public class Graph {
    static void SetDefaultDevice(String device, GraphDef.Builder g){
        for (int i = 0; i < g.getNodeCount(); i++) {
           NodeDef.Builder node = g.getNodeBuilder(i);
            if (node.getDevice().isEmpty()){
                node.setDevice(device);
            }
        }
    }


    void Def(){
        NodeDef.Builder b = NodeDef.newBuilder();
        final long n = 4 ;
        b.getAttr().put("n", AttrValue.newBuilder().setI(n).build());
        b.build();
    }

    static OpList loadOps(){
        try {

            BufferedReader r = new BufferedReader(new InputStreamReader( new FileInputStream("/home/fmilo/workspace/tensorflow/tensorflow/core/ops/ops.pbtxt")));
            OpList.Builder b = OpList.newBuilder();
            TextFormat.getParser().merge(r,b);
            return b.build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return OpList.newBuilder().build();
    }
}
