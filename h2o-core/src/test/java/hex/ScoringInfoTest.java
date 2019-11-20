package hex;

import org.apache.commons.lang.ArrayUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import water.IcedWrapper;
import water.TestBase;
import water.util.TwoDimTable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ScoringInfoTest extends TestBase {


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
        scoringInfo.scored_valid._pr_auc = 0.6607;
        scoringInfo.scored_valid._lift = 1.8014;
        scoringInfo.scored_valid._classError = 0.3299;

        scoringInfo.scored_xval = new ScoreKeeper();
        scoringInfo.scored_xval._rmse = 0.4641;
        scoringInfo.scored_xval._logloss = 0.6194;
        scoringInfo.scored_xval._r2 = 0.1308;
        scoringInfo.scored_xval._AUC = 0.7095;
        scoringInfo.scored_xval._pr_auc = 0.6095;
        scoringInfo.scored_xval._lift = 1.6670;
        scoringInfo.scored_xval._classError = 0.3703;

        ScoringInfo[] scoringInfos = new ScoringInfo[]{scoringInfo};
        TwoDimTable scoringHistoryTable = ScoringInfo.createScoringHistoryTable(scoringInfos, true, true, ModelCategory.Binomial, false);
        assertNotNull(scoringHistoryTable);

        IcedWrapper[][] cellValues = scoringHistoryTable.getCellValues();
        String[] cellHeaders = scoringHistoryTable.getColHeaders();
        assertEquals(23, cellValues[0].length);

        // Test may run in different timezone. Expected timestmap can not be hardcoded.
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        assertEquals(fmt.print(scoringInfo.time_stamp_ms), cellValues[0][0].get());

        assertEquals("10.000 sec", cellValues[0][ArrayUtils.indexOf(cellHeaders, "Duration")].get());
        assertEquals(scoringInfo.scored_train._rmse, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training RMSE")].get());
        assertEquals(scoringInfo.scored_train._logloss, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training LogLoss")].get());
        assertEquals(scoringInfo.scored_train._r2, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training r2")].get());
        assertEquals(scoringInfo.training_AUC._auc, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training AUC")].get());
        assertEquals(scoringInfo.scored_train._lift, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training Lift")].get());
        assertEquals(scoringInfo.scored_train._classError, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Training Classification Error")].get());

        assertEquals(scoringInfo.scored_valid._rmse, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation RMSE")].get());
        assertEquals(scoringInfo.scored_valid._logloss, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation LogLoss")].get());
        assertEquals(scoringInfo.scored_valid._r2, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation r2")].get());
        assertEquals(scoringInfo.scored_valid._AUC, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation AUC")].get());
        assertEquals(scoringInfo.scored_valid._pr_auc, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation pr_auc")].get());
        assertEquals(scoringInfo.scored_valid._lift, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation Lift")].get());
        assertEquals(scoringInfo.scored_valid._classError, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Validation Classification Error")].get());

        assertEquals(scoringInfo.scored_xval._rmse, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation RMSE")].get());
        assertEquals(scoringInfo.scored_xval._logloss, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation LogLoss")].get());
        assertEquals(scoringInfo.scored_xval._r2, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation r2")].get());
        assertEquals(scoringInfo.scored_xval._AUC, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation AUC")].get());
        assertEquals(scoringInfo.scored_xval._pr_auc, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation pr_auc")].get());
        assertEquals(scoringInfo.scored_xval._lift, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation Lift")].get());
        assertEquals(scoringInfo.scored_xval._classError, cellValues[0][ArrayUtils.indexOf(cellHeaders, "Cross-Validation Classification Error")].get());

    }
}
