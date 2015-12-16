package ai.h2o.automl.transforms;

/**
 * Stupid class just to get the proverbial ball rolling.
 * Eventually, will want to javassist (i.e. fuse) transforms over multiple vecs
 * (rather than 1 at a time, which is the current limitation of this class).
 *
 * A pile of heuristics (rules) and models drive the choice of transforms here.
 *
 *
 *
 * Some heuristics:
 *    Allowing users to do a log(), or log1p() quickly would be nice.
 *    Trying it behind their back might be useful. I've started to rattle off
 *        "if your numbers span more than an order of magnitude,
 *         you might try a log transform"
 *    quite often. Reasonable enough heuristic. Heuristics are good for DSiaB.
 *
 *   Hard-coding a few common things to look for: "Year" is an integer, but log
 *   transforming it likely doesn't make as much sense; special transforms exist
 *   for that, so we might want to understand the difference and be willing to guess.
 */
public class Transform {

}
