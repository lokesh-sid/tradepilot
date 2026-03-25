package tradingbot.bot.service.backtest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.service.BinanceFuturesService.Candle;

@Component
public class HistoricalDataLoader {

    public List<Candle> loadFromCsv(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return parseCsv(br);
        } catch (Exception e) {
            throw new BotOperationException("load_historical_data", "Failed to load historical data from " + filePath, e);
        }
    }

    public List<Candle> loadFromStream(InputStream inputStream) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            return parseCsv(br);
        } catch (Exception e) {
            throw new BotOperationException("load_historical_data", "Failed to load historical data from stream", e);
        }
    }

    private List<Candle> parseCsv(BufferedReader br) throws IOException {
        List<Candle> candles = new ArrayList<>();
        String line;
        boolean header = true;
        while ((line = br.readLine()) != null) {
            if (header) {
                header = false;
                continue; // Skip header
            }
            String[] values = line.split(",");
            // Assuming CSV format: OpenTime, Open, High, Low, Close, Volume, CloseTime
            Candle candle = new Candle();
            candle.setOpenTime(Long.parseLong(values[0]));
            candle.setOpen(new BigDecimal(values[1]));
            candle.setHigh(new BigDecimal(values[2]));
            candle.setLow(new BigDecimal(values[3]));
            candle.setClose(new BigDecimal(values[4]));
            candle.setVolume(new BigDecimal(values[5]));
            candle.setCloseTime(Long.parseLong(values[6]));
            candles.add(candle);
        }
        return candles;
    }
}
