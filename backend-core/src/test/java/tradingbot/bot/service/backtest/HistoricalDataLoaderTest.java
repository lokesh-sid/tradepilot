package tradingbot.bot.service.backtest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tradingbot.bot.service.BinanceFuturesService.Candle;

class HistoricalDataLoaderTest {

    @TempDir
    File tempDir;

    @Test
    void shouldLoadCandlesFromCsv() throws IOException {
        File csvFile = new File(tempDir, "test_data.csv");
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("OpenTime,Open,High,Low,Close,Volume,CloseTime\n");
            writer.write("1600000000000,100.0,105.0,95.0,102.0,1000.0,1600000060000\n");
            writer.write("1600000060000,102.0,108.0,101.0,107.0,1500.0,1600000120000\n");
        }

        HistoricalDataLoader loader = new HistoricalDataLoader();
        List<Candle> candles = loader.loadFromCsv(csvFile.getAbsolutePath());

        assertEquals(2, candles.size());
        
        Candle first = candles.get(0);
        assertEquals(1600000000000L, first.getOpenTime());
        assertEquals(100.0, first.getOpen().doubleValue());
        assertEquals(102.0, first.getClose().doubleValue());
        
        Candle second = candles.get(1);
        assertEquals(1600000060000L, second.getOpenTime());
        assertEquals(107.0, second.getClose().doubleValue());
    }
    
    @Test
    void shouldThrowExceptionForInvalidFile() {
        HistoricalDataLoader loader = new HistoricalDataLoader();
        assertThrows(RuntimeException.class, () -> loader.loadFromCsv("non_existent_file.csv"));
    }
}
