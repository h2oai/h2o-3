package hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import water.IcedWrapper;
import water.util.TwoDimTable;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ScoringInfoTest {


    @Test
    public void createScoringHistoryTable() {
        ScoringInfo scoringInfo = new ScoringInfo();
        scoringInfo.time_stamp_ms = 123456789123456L;
        scoringInfo.total_training_time_ms = 10000;
        scoringInfo.scored_train = new ScoreKeeper();
        scoringInfo.scored_train._rmse = 0.4477;
        scoringInfo.scored_train._logloss = 0.5857;
        scoringInfo.scored_train._r2 = 0.1910;
        AUC2.AUCBuilder aucBuilder = new AUC2.AUCBuilder(2);
        aucBuilder._n = 2;
        scoringInfo.training_AUC = new AUC2(aucBuilder);
        scoringInfo.scored_train._lift = 1.8014;
        scoringInfo.scored_train._classError = 0.3299;

        scoringInfo.scored_valid = new ScoreKeeper();
        scoringInfo.scored_valid._rmse = 0.4477;
        scoringInfo.scored_valid._logloss = 0.5857;
        scoringInfo.scored_valid._r2 = 0.5857;
        scoringInfo.scored_valid._AUC = 0.7607;
        scoringInfo.scored_valid._lift = 1.8014;
        scoringInfo.scored_valid._classError = 0.3299;

        scoringInfo.scored_xval = new ScoreKeeper();
        scoringInfo.scored_xval._rmse = 0.4641;
        scoringInfo.scored_xval._logloss = 0.6194;
        scoringInfo.scored_xval._r2 = 0.1308;
        scoringInfo.scored_xval._AUC = 0.7095;
        scoringInfo.scored_xval._lift = 1.6670;
        scoringInfo.scored_xval._classError = 0.3703;

        ScoringInfo[] scoringInfos = new ScoringInfo[]{scoringInfo};
        TwoDimTable scoringHistoryTable = ScoringInfo.createScoringHistoryTable(scoringInfos, true, true, ModelCategory.Binomial, false);
        assertNotNull(scoringHistoryTable);

        IcedWrapper[][] cellValues = scoringHistoryTable.getCellValues();

        assertEquals("5882-03-11 01:32:03", cellValues[0][0].get());
        assertEquals("10.000 sec", cellValues[0][1].get());
        assertEquals(scoringInfo.scored_train._rmse, cellValues[0][2].get());
        assertEquals(scoringInfo.scored_train._logloss, cellValues[0][3].get());
        assertEquals(scoringInfo.scored_train._r2, cellValues[0][4].get());
        assertEquals(scoringInfo.training_AUC._auc, cellValues[0][5].get());
        assertEquals(scoringInfo.scored_train._lift, cellValues[0][6].get());
        assertEquals(scoringInfo.scored_train._classError, cellValues[0][7].get());

        assertEquals(scoringInfo.scored_valid._rmse, cellValues[0][8].get());
        assertEquals(scoringInfo.scored_valid._logloss, cellValues[0][9].get());
        assertEquals(scoringInfo.scored_valid._r2, cellValues[0][10].get());
        assertEquals(scoringInfo.scored_valid._AUC, cellValues[0][11].get());
        assertEquals(scoringInfo.scored_valid._lift, cellValues[0][12].get());
        assertEquals(scoringInfo.scored_valid._classError, cellValues[0][13].get());

        assertEquals(scoringInfo.scored_xval._rmse, cellValues[0][14].get());
        assertEquals(scoringInfo.scored_xval._logloss, cellValues[0][15].get());
        assertEquals(scoringInfo.scored_xval._r2, cellValues[0][16].get());
        assertEquals(scoringInfo.scored_xval._AUC, cellValues[0][17].get());
        assertEquals(scoringInfo.scored_xval._lift, cellValues[0][18].get());
        assertEquals(scoringInfo.scored_xval._classError, cellValues[0][19].get());

    }
}