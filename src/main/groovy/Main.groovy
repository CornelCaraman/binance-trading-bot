import com.google.gson.JsonObject
import com.webcerebrium.binance.api.BinanceApi
import com.webcerebrium.binance.api.BinanceApiException
import com.webcerebrium.binance.datatype.BinanceAggregatedTrades
import com.webcerebrium.binance.datatype.BinanceOrder
import com.webcerebrium.binance.datatype.BinanceOrderPlacement
import com.webcerebrium.binance.datatype.BinanceOrderSide
import com.webcerebrium.binance.datatype.BinanceOrderType
import com.webcerebrium.binance.datatype.BinanceSymbol

/**
 * Created by gcretu on 12/10/17.
 */
class Main {
  public static void main(String[] args) {
    int iterationIndex = 0;
    String apiKey = '_'
    String secret = '_'
    BinanceApi api = new BinanceApi(apiKey, secret);
/*      //market price, current chart position
      System.out.println(api.pricesMap().get("IOTAETH"))

      System.out.println(api.allBookTickersMap().findAll { it.key == 'IOTAETH' }.each { println it });

      */

    while (true) {
      try {
        iterationIndex++
        println 'iterration number : ' + iterationIndex
        BinanceSymbol symbol = BinanceSymbol.valueOf("IOTAETH");
        Map<String, Double> priceFreeMap = api.balances().collectEntries { it ->
          [it["asset"].toString().replace("\"", ""), Double.parseDouble(it["free"].toString().replace("\"", ""))]
        }

        Map<String, Double> priceBlockedMap = api.balances().collectEntries { it ->
          [it["asset"].toString().replace("\"", ""), Double.parseDouble(it["locked"].toString().replace("\"", ""))]
        }


        Double ethAmount = priceFreeMap["ETH"]
        println 'priceFreeMap : ' + priceFreeMap
        Thread.sleep(2500);

        double maxPrice
        double maxAmount
        JsonObject depth = api.depth(symbol, 5);
        //get sell asks
        depth
          .get("asks").getAsJsonArray()
          .collectEntries {
          [Double.parseDouble(it[0].toString().replace("\"", "")), Double.parseDouble(it[1].toString().replace("\"", ""))]
        }
        .sort { a, b -> b.value <=> a.value }
          .eachWithIndex { it, index ->
          if (index == 0) {
            maxPrice = it.key
            maxAmount = it.value
          }
        }

        Double minPrice
        Double minAmount
        //get sell asks
        depth
          .get("bids").getAsJsonArray()
          .collectEntries {
          [Double.parseDouble(it[0].toString().replace("\"", "")), Double.parseDouble(it[1].toString().replace("\"", ""))]
        }
        .sort { a, b -> b.value <=> a.value }
          .eachWithIndex { it, index ->
          if (index == 0) {
            minPrice = it.key
            minAmount = it.value
          }
        }

        println 'minPrice = ' + minPrice
        println 'maxPrice = ' + maxPrice


        try {
          if (priceFreeMap["IOTA"] >= 1) {
            BinanceOrderPlacement placement = new BinanceOrderPlacement(symbol, BinanceOrderSide.SELL);
            placement.setType(BinanceOrderType.LIMIT);
            placement.setPrice(BigDecimal.valueOf(maxPrice))
            placement.setQuantity(BigDecimal.valueOf((int) priceFreeMap["IOTA"]))
            api.createOrder(placement)
            println 'Placing sell order (coins amount= ' + priceFreeMap["IOTA"] + '): ' + placement
          }
        } catch (BinanceApiException e) {
          System.out.println("ERROR: " + e.getMessage());
        }

        try {
          if (ethAmount >= minPrice) {
            int amount = (int) ethAmount / minPrice
            BinanceOrderPlacement placement = new BinanceOrderPlacement(symbol, BinanceOrderSide.BUY);
            placement.setType(BinanceOrderType.LIMIT);
            placement.setPrice(BigDecimal.valueOf(minPrice))
            placement.setQuantity(BigDecimal.valueOf(amount))
            api.createOrder(placement)
            println 'Placing buy order (coins amount= ' + amount + '): ' + placement
          }
        } catch (BinanceApiException e) {
          System.out.println("ERROR: " + e.getMessage());
        }

        List<BinanceOrder> openOrders = api.openOrders(symbol)

        if (openOrders) {
          openOrders.findAll { it.symbol == symbol.get() && it.side == BinanceOrderSide.SELL }.each { order ->
            println 'current sell order : ' + order
            if (order.price.doubleValue() != maxPrice) {

            api.deleteOrderById(symbol, order.orderId)

            BinanceOrderPlacement placement = new BinanceOrderPlacement(symbol, BinanceOrderSide.SELL);
            placement.setType(BinanceOrderType.LIMIT);
            placement.setPrice(BigDecimal.valueOf(maxPrice));
            placement.setQuantity(order.origQty)
            api.createOrder(placement)
            println 'Updating sell order'
            println 'was : ' + order
            println 'now is : ' + placement
             }
          }

          openOrders.findAll { it.symbol == symbol.get() && it.side == BinanceOrderSide.BUY }.each { order ->
            println 'current buy order : ' + order
             if (order.price.doubleValue() > minPrice) {

            api.deleteOrderById(symbol, order.orderId)

            BinanceOrderPlacement placement = new BinanceOrderPlacement(symbol, BinanceOrderSide.BUY);
            placement.setType(BinanceOrderType.LIMIT);
            placement.setPrice(BigDecimal.valueOf(minPrice));
            placement.setQuantity(order.origQty)
            api.createOrder(placement)
            println 'Updating buy order'
            println 'was : ' + order
            println 'now is : ' + placement
            }
          }

        }

      } catch (BinanceApiException e) {
        System.out.println("ERROR: " + e.getMessage());
      }
    }

  }
}
