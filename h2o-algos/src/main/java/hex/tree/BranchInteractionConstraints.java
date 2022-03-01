package hex.tree;

import water.Iced;

import java.util.HashSet;
import java.util.Set;

public class BranchInteractionConstraints extends Iced<BranchInteractionConstraints> {
    Set<Integer> allowedInteractionIndices;

    public BranchInteractionConstraints(){
        this.allowedInteractionIndices = new HashSet<>();
    }
    
    public BranchInteractionConstraints(Set<Integer> allowedInteractionIndices){
        this.allowedInteractionIndices = allowedInteractionIndices;
    }
    
    public boolean isAllowedIndex(int i){
        return allowedInteractionIndices.contains(i);
    }
    
    public BranchInteractionConstraints copy(){
        Set<Integer> aicCopy = new HashSet<>(allowedInteractionIndices);
        return new BranchInteractionConstraints(aicCopy);
    }
    
    public void addAll(Set<Integer> newAllowedInteractionIndices){
        this.allowedInteractionIndices.addAll(newAllowedInteractionIndices);
    }
    
    public void add(int i){
        this.allowedInteractionIndices.add(i);
    }
    
    public Set<Integer> intersection(Set<Integer> set){
        Set<Integer> output = new HashSet<>(set);
        output.retainAll(this.allowedInteractionIndices);
        return output;
    }
    
    public BranchInteractionConstraints nextLevelInteractionConstraints(GlobalInteractionConstraints ics, int colIndex){
        assert ics != null : "Interaction constraints: Global interaction constraints object cannot be null.";
        assert ics.allowedInteractionContainsColumn(colIndex) : "Input column index should be in the allowed interaction map.";
        assert this.allowedInteractionIndices != null : "Interaction constraints: Branch allowed interaction set cannot be null.";
        Set<Integer> allowedInteractions = ics.getAllowedInteractionForIndex(colIndex);
        Set<Integer> intersection = intersection(allowedInteractions);
        return new BranchInteractionConstraints(intersection);
    }
}
