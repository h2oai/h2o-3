package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import hex.genmodel.mojopipeline.parsing.ParameterParser;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringSplitTransform extends MojoTransform {
    
    String _regex = null;
    int _numberOfOutputCols;

    StringSplitTransform(int[] iindices, int[] oindices, String regex) {
        super(iindices, oindices);
        _regex = regex;
        _numberOfOutputCols = oindices.length;
    }

    @Override
    public void transform(MojoFrame frame) {
        String[] a = (String[]) frame.getColumnData(iindices[0]);
        String[][] outputs  =  new String[_numberOfOutputCols][];
        for (int j = 0; j < _numberOfOutputCols; j++) {
            outputs[j] = (String[]) frame.getColumnData(oindices[j]);
        } 
        for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
            if (a[i] != null) {
                String[] split = a[i].split(_regex);
                int nCol = Math.min(_numberOfOutputCols, split.length);
                for (int j = 0; j < nCol; j++) {
                    outputs[j][i] = split[j];
                }
            }
        }
    }

    public static class Factory implements MojoTransformBuilderFactory {

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.StringSplitTransform";
       
        public static boolean functionExists(String functionName) {
            return functionName.equals("strsplit");
        }

        @Override
        public String transformerName() {
            return TRANSFORMER_ID;
        }

        @Override
        public MojoTransform createBuilder(MojoFrameMeta meta,
                                           int[] iindcies, int[] oindices,
                                           Map<String, Object> params,
                                           ReaderBackend backend) {
            Object regexObj = params.get("split");
            if (regexObj== null) {
                throw new IllegalArgumentException("The 'split' param is not passed to 'strsplit' function!");
            }
            String regex = (String)regexObj;
            
            return new StringSplitTransform(iindcies, oindices, regex);
        }
    }
}
