import com.google.gson.JsonObject
import com.webcerebrium.binance.api.BinanceApi
import com.webcerebrium.binance.api.BinanceApiException
import com.webcerebrium.binance.datatype.BinanceAggregatedTrades
import com.webcerebrium.binance.datatype.BinanceSymbol

/**
 * Created by gcretu on 12/10/17.
 */
class Main {
  public static void main(String[] args) {
    String apiKey = '--'
    String secret = '--'
    BinanceApi api = new BinanceApi(apiKey, secret);
    try {
      //market price, current chart position
      System.out.println(api.pricesMap().get("IOTAETH"))


      List<BinanceAggregatedTrades> aggTrades =  api.aggTrades(BinanceSymbol.valueOf('IOTAETH'),10,null)
      for(BinanceAggregatedTrades aggTrade : aggTrades){
        println aggTrade
      }
      println ""

      System.out.println(api.allBookTickersMap().findAll {it.key=='IOTAETH'}.each {println it});

      BinanceSymbol symbol = BinanceSymbol.valueOf("IOTAETH");
      JsonObject depth = (new BinanceApi()).depth(symbol);
      println ""

      //get sell bids
      depth.get("bids") .getAsJsonArray()
        //.sort {a,b -> Double.parseDouble(a[0].toString().replace('\"',"")) <=> Double.parseDouble(b[0].toString())}
        .collect {
        it[0].toString().replace("\"","")
      }
        .sort{a,b -> Double.parseDouble(b) <=> Double.parseDouble(a)}
      .each {println it}
      println ""

      //get buy bids
      depth.get("asks").getAsJsonArray().each {println "ASKS=" + it}

/*      BinanceSymbol symbol = BinanceSymbol.valueOf("IOTAETH");
      System.out.println((new BinanceApi()).ticker24hr(symbol));*/

    } catch (BinanceApiException e) {
      System.out.println( "ERROR: " + e.getMessage());
    }
  }
}
