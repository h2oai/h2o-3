package hex.genmodel.tools;

import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxEdgeLabelLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.util.mxCellRenderer;
import hex.genmodel.algos.tree.ConvertTreeOptions;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.TreeBackedMojoModel;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.io.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class JgraphtPrintMojo extends PrintMojo implements MojoPrinter {

    @Override
    public void run() throws Exception {
        if (!Format.png.equals(format)){
            super.run();
        } else {
            validateArgs();
            if (genModel instanceof TreeBackedMojoModel){
                TreeBackedMojoModel treeBackedModel = (TreeBackedMojoModel) genModel;
                ConvertTreeOptions options = new ConvertTreeOptions().withTreeConsistencyCheckEnabled();
                final SharedTreeGraph g = treeBackedModel.convert(treeToPrint, null, options);
                printPng(g);
            }
            else {
                System.out.println("ERROR: Unknown MOJO type");
                System.exit(1);
            }
        } 
    }

    @Override
    public boolean supportsFormat(Format format){
        return true;
    }
    
    private void printPng(SharedTreeGraph trees) throws IOException, ImportException {
        Path outputDirectoryPath = Paths.get(outputFileName);
        int numberOfTrees = trees.subgraphArray.size();
        if (numberOfTrees > 1) { 
            if (outputFileName == null) {
                outputFileName = Paths.get("").toString();
            }
            if (Files.exists(outputDirectoryPath) && !Files.isDirectory(outputDirectoryPath)) {
                Files.delete(outputDirectoryPath);
            }
            if (!Files.exists(outputDirectoryPath)) {
                Files.createDirectory(outputDirectoryPath);
            }
        }
        for (SharedTreeSubgraph tree : trees.subgraphArray) {
            Path dotSourceFilePath = Files.createTempFile("", tmpOutputFileName);
            try (FileOutputStream fosTemp = new FileOutputStream(dotSourceFilePath.toFile()); PrintStream osTemp = new PrintStream(fosTemp)) {
                tree.printDot(osTemp, maxLevelsToPrintPerEdge, detail, optionalTitle, pTreeOptions, true);
                generateOutputPng(dotSourceFilePath, getPngName(numberOfTrees, tree.name));
            }
            Files.delete(dotSourceFilePath);
        }
    }

    private void generateOutputPng(Path dotSourceFilePath, String treeName) throws ImportException, IOException {
        LabeledVertexProvider vertexProvider = new LabeledVertexProvider();
        LabeledEdgesProvider edgesProvider = new LabeledEdgesProvider();
        ComponentUpdater componentUpdater = new ComponentUpdater();
        DOTImporter<LabeledVertex, LabeledEdge> importer = new DOTImporter<>(vertexProvider, edgesProvider, componentUpdater);
        DirectedMultigraph<LabeledVertex, LabeledEdge> result = new DirectedMultigraph<>(LabeledEdge.class);
        try (FileInputStream is = new FileInputStream(dotSourceFilePath.toFile()); Reader reader = new InputStreamReader(is)) {
            importer.importGraph(result, reader);
            JGraphXAdapter<LabeledVertex, LabeledEdge> graphAdapter = new JGraphXAdapter<LabeledVertex, LabeledEdge>(result);
            mxIGraphLayout treeLayout = new mxCompactTreeLayout(graphAdapter, true);
            mxIGraphLayout nonOverlappingEdgesLayout = new mxEdgeLabelLayout(graphAdapter);
            treeLayout.execute(graphAdapter.getDefaultParent());
            nonOverlappingEdgesLayout.execute(graphAdapter.getDefaultParent());
            BufferedImage image = mxCellRenderer.createBufferedImage(graphAdapter, null, 2, Color.WHITE, true, null);
            if (outputFileName != null) {
                ImageIO.write(image, "PNG", new File(treeName));
            } else {
                ImageIO.write(image, "PNG", System.out);
            }
        }
    }

    protected String getPngName(int numberOfTrees, String treeName) {
        if (numberOfTrees == 1) {
            return outputFileName;
        } else {
            return outputFileName + "/" + treeName.replaceAll("\\s+", "").replaceAll(",", "_") + ".png";
        }

    }

    private class LabeledVertexProvider implements VertexProvider<LabeledVertex> {
        @Override
        public LabeledVertex buildVertex(String id, Map<String, Attribute> attributes) {
            return new LabeledVertex(id, attributes.get("label").toString());
        }
    }

    private class LabeledEdgesProvider implements EdgeProvider<LabeledVertex, LabeledEdge> {

        @Override
        public LabeledEdge buildEdge(LabeledVertex f, LabeledVertex t, String l, Map<String, Attribute> attrs) {
            return new LabeledEdge(l);
        }
    }
    private class ComponentUpdater implements org.jgrapht.io.ComponentUpdater<LabeledVertex>{
        @Override
        public void update(LabeledVertex v, Map<String, Attribute> attrs) {
        }
    }

    private class LabeledEdge extends DefaultEdge {
        private String label;

        /**
         * Constructs a relationship edge
         *
         * @param label the label of the new edge.
         *
         */
        public LabeledEdge(String label)
        {
            this.label = label;
        }

        /**
         * Gets the label associated with this edge.
         *
         * @return edge label
         */
        public String getLabel()
        {
            return label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    static class LabeledVertex
    {
        private String id;
        private String label;

        public LabeledVertex(String id)
        {
            this(id, null);
        }

        public LabeledVertex(String id, String label)
        {
            this.id = id;
            this.label = label;
        }

        @Override
        public int hashCode()
        {
            return (id == null) ? 0 : id.hashCode();
        }

        @Override
        public String toString()
        {
            return label;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LabeledVertex other = (LabeledVertex) obj;
            if (id == null) {
                return other.id == null;
            } else {
                return id.equals(other.id);
            }
        }
    }
    
}
