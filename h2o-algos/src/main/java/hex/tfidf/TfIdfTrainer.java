package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedInt;
import water.util.IcedLong;

import java.util.HashMap;

public class TfIdfTrainer extends MRTask<TfIdfTrainer> {

    /**
     * Words delimiter in documents.
     */
    // TODO
    private static final String WORDS_DELIMITER = " ";

    // IN
    /**
     * Defines indices of words in the output Dataframe.
     */
    private IcedHashMap<BufferedString, IcedInt> _wordsIndices;

    // OUT
    /**
     * Total words count for each document.
     */
    // TODO: Total words counts computation
    public IcedLong[] _totalWordsCounts;


    public TfIdfTrainer(IcedHashMap<BufferedString, IcedInt> wordsIndices) {
        _wordsIndices = wordsIndices;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk inputChunk = cs[0];

        for (int row = 0; row < inputChunk._len; row++) {
            if (inputChunk.isNA(row))
                continue; // Ignore NAs

            String[] words = inputChunk.atStr(new BufferedString(), row).toString().split(WORDS_DELIMITER);

            HashMap<BufferedString, IcedLong> wordsCount = new HashMap<>();

            for (String word : words) {
                BufferedString buffWord = new BufferedString(word);

                IcedLong count = wordsCount.get(buffWord);
                if (count != null)
                    wordsCount.put(buffWord, new IcedLong(count._val + 1));
                else
                    wordsCount.put(buffWord, new IcedLong(1));
            }

            _wordsIndices.forEach((buffWord, index) -> {
                IcedLong count = wordsCount.get(buffWord);
                if (count != null)
                    ncs[index._val].addNum(count._val);
                else
                    ncs[index._val].addNum(0);
            });
        }
    }
}
