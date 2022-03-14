package hex.tree;

import water.Iced;
import water.util.IcedHashSet;
import water.util.IcedInt;

import java.util.HashSet;
import java.util.Set;

/**
 * Local branch interaction constraints class to save information about allowed interaction between columns indices
 */
public class BranchInteractionConstraints extends Iced<BranchInteractionConstraints> {
    
    // Set of allowed column indices in current split but with information from previous split decision
    // Set<Integer> allowedInteractionIndices;
    IcedHashSet<IcedInt> allowedInteractionIndices;
    
    public BranchInteractionConstraints(IcedHashSet<IcedInt> allowedInteractionIndices){
        this.allowedInteractionIndices = allowedInteractionIndices;
    }
    
    public boolean isAllowedIndex(int i){
        return allowedInteractionIndices.contains(i);
    }

    /**
     * Important method to decide which indices are allowed for the next level of constraints.
     * It makes intersection between current allowed indices and input indices to make sure the local constraint
     * satisfy the global interaction constraints setting.  
     * @param set input set 
     * @return intersection of branch set and input set
     */
    public IcedHashSet<IcedInt> intersection(IcedHashSet<IcedInt> set){
        IcedHashSet<IcedInt> output = new IcedHashSet<>();
        for(IcedInt i: set){
            if (allowedInteractionIndices.contains(i)) {
                output.add(i);
            }
        }
        return output;
    }

    /**
     * Decide which column indices is allowed to be used for the next split in the next level of a tree.
     * @param ics global interaction constraint object generated from input interaction constraints
     * @param colIndex column index of the split to decide allowed indices for the next level of constraint
     * @return new branch interaction object for the next level of the tree
     */
    public BranchInteractionConstraints nextLevelInteractionConstraints(GlobalInteractionConstraints ics, int colIndex){
        assert ics != null : "Interaction constraints: Global interaction constraints object cannot be null.";
        assert ics.allowedInteractionContainsColumn(colIndex) : "Input column index should be in the allowed interaction map.";
        assert this.allowedInteractionIndices != null : "Interaction constraints: Branch allowed interaction set cannot be null.";
        IcedHashSet<IcedInt> allowedInteractions = ics.getAllowedInteractionForIndex(colIndex);
        IcedHashSet<IcedInt> intersection = intersection(allowedInteractions);
        return new BranchInteractionConstraints(intersection);
    }
}
