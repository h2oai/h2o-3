package water.api;

import hex.HitRatio;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

public class HitRatioBase<I extends HitRatio, S extends HitRatioBase<I, S>> extends Schema<I, S> {
    @API(help="Domain of the actual response", direction=API.Direction.OUTPUT)
    public String [] actual_domain;

    @API(help="Hit ratios for k = 1,...,K", direction=API.Direction.OUTPUT)
    public float[] hit_ratios;
}
