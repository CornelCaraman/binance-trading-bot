import com.webcerebrium.binance.api.BinanceApi
import com.webcerebrium.binance.api.BinanceApiException
import com.webcerebrium.binance.datatype.BinanceOrder
import com.webcerebrium.binance.datatype.BinanceOrderPlacement
import com.webcerebrium.binance.datatype.BinanceOrderSide
import com.webcerebrium.binance.datatype.BinanceOrderType
import com.webcerebrium.binance.datatype.BinanceSymbol

/**
 * Created by gcretu on 12/10/17.
 */
class Main {
  String apiKey = '---'
  String secret = '---'
  String binanceSymbol = 'TRXETH'
  int iterationIndex = 0
  BinanceApi binanceApi

  private BinanceApi getApiObject() {
    if (!binanceApi) binanceApi = new BinanceApi(apiKey, secret)
    binanceApi
  }

  private BinanceSymbol getBinanceSymbol() {
    BinanceSymbol.valueOf(binanceSymbol)
  }

  private incrementAndGetIterationNumber() {
    iterationIndex++
    iterationIndex
  }

  private Map<String, Double> getAvailableBalances(BinanceApi api) {
    Map result = api.balances().collectEntries { it ->
      [it["asset"].toString().replace("\"", ""), Double.parseDouble(it["free"].toString().replace("\"", ""))]
    }
    println 'priceFreeMap : ' + result
    result
  }

  private Double getCoinAmount(Map<String, Double> balances, String coin) {
    balances[coin]
  }

  private void pauseExecutionForMillis(long millis) {
    Thread.sleep(millis)
  }

  private double getSellMaxPrice(BinanceApi api, BinanceSymbol symbol, int depth) {
    //get sell asks
    double maxSellPrice = 10000000
    api.depth(symbol, 10)
      .get("asks").getAsJsonArray()
      .collectEntries {
      [Double.parseDouble(it[0].toString().replace("\"", "")), Double.parseDouble(it[1].toString().replace("\"", ""))]
    }
    .sort { a, b -> b.value <=> a.value }
      .eachWithIndex { it, index ->
      if (index == 0) {
        maxSellPrice = it.key
      }
    }
    println 'maxPrice = ' + maxSellPrice
    maxSellPrice
  }

  private double getBuyMinPrice(BinanceApi api, BinanceSymbol symbol, int depth) {
    //get sell asks
    double minBuyPrice = 0
    api.depth(symbol, 10)
      .get("bids").getAsJsonArray()
      .collectEntries {
      [Double.parseDouble(it[0].toString().replace("\"", "")), Double.parseDouble(it[1].toString().replace("\"", ""))]
    }
    .sort { a, b -> b.value <=> a.value }
      .eachWithIndex { it, index ->
      if (index == 0) {
        minBuyPrice = it.key
      }
    }
    println 'minPrice = ' + minBuyPrice
    minBuyPrice
  }

  private void placeSellOrder(double maxPrice, int quantity, BinanceSymbol binanceSymbol) {
    try {
      BinanceOrderPlacement placement = new BinanceOrderPlacement(binanceSymbol, BinanceOrderSide.SELL);
      placement.setType(BinanceOrderType.LIMIT);
      placement.setPrice(BigDecimal.valueOf(maxPrice))
      placement.setQuantity(BigDecimal.valueOf(quantity))
      getApiObject().createOrder(placement)
      println 'Placing sell order (coins amount= ' + quantity + '): ' + placement
    }
    catch (BinanceApiException e) {
      System.out.println("ERROR: " + e.getMessage());
    }
  }

  private void placeBuyOrder(double minPrice, int quantity, BinanceSymbol binanceSymbol) {
    try {
      if (quantity >= minPrice) {
        int amount = (int) quantity / minPrice
        BinanceOrderPlacement placement = new BinanceOrderPlacement(binanceSymbol, BinanceOrderSide.BUY);
        placement.setType(BinanceOrderType.LIMIT);
        placement.setPrice(BigDecimal.valueOf(minPrice))
        placement.setQuantity(BigDecimal.valueOf(amount))
        getApiObject().createOrder(placement)
        println 'Placing buy order (coins amount= ' + amount + '): ' + placement
      }
    } catch (BinanceApiException e) {
      System.out.println("ERROR: " + e.getMessage());
    }
  }

  private List<BinanceOrder> getOpenOrdersForSymbol(BinanceSymbol binanceSymbol) {
    getBinanceApi().openOrders(binanceSymbol)
  }

  private List<BinanceOrder> getSellOrders(List<BinanceOrder> binanceOrders) {
    binanceOrders.findAll { it.symbol == getBinanceSymbol().get() && it.side == BinanceOrderSide.SELL }
  }

  private List<BinanceOrder> getBuyOrders(List<BinanceOrder> binanceOrders) {
    binanceOrders.findAll { it.symbol == getBinanceSymbol().get() && it.side == BinanceOrderSide.BUY }
  }

  private boolean shouldUpdateSellOrder(BinanceOrder order, double currentMaxPrice) {
    println 'current sell order : ' + order
    order.price.doubleValue() != currentMaxPrice
  }

  private boolean shouldUpdateBuyOrder(BinanceOrder order, double currentMinPrice) {
    println 'current buy order : ' + order
    order.price.doubleValue() > currentMinPrice
  }

  private boolean shouldSellCoin(double coinAmount, double currentMaxPrice) {
    BinanceOrder previousBuyOrder = getPreviousBuyOrder(getBinanceSymbol())
    double currentSellTotalEthAmount = coinAmount * currentMaxPrice
    double previousBuyTotalEthAmount = coinAmount * previousBuyOrder.price
    double buySellPriceDifference = currentSellTotalEthAmount - previousBuyTotalEthAmount
    double minProfit = previousBuyTotalEthAmount / 100
    coinAmount >= 1 && currentMaxPrice > previousBuyOrder.price && buySellPriceDifference > 0 && buySellPriceDifference > minProfit
  }

  private boolean shouldBuyCoin(double coinAmount, double minPrice) {
    BinanceOrder previousSellOrder = getPreviousSellOrder(getBinanceSymbol())
    double currentBuyTotalEthAmount = ((int) coinAmount / minPrice) * minPrice
    double previousSellTotalEthAmount = previousSellOrder.price * previousSellOrder.origQty
    double buySellPriceDifference = previousSellTotalEthAmount - currentBuyTotalEthAmount
    double minProfit = previousSellTotalEthAmount / 100
    coinAmount >= minPrice && minPrice < previousSellOrder.price && buySellPriceDifference > 0 && buySellPriceDifference > minProfit
  }

  private BinanceOrder getPreviousSellOrder(BinanceSymbol binanceSymbol) {
    getApiObject().allOrders(binanceSymbol).findAll { order -> order.side == BinanceOrderSide.SELL }.sort { a, b -> b.time <=> a.time }.first()
  }

  private BinanceOrder getPreviousBuyOrder(BinanceSymbol binanceSymbol) {
    getApiObject().allOrders(binanceSymbol).findAll { order -> order.side == BinanceOrderSide.BUY }.sort { a, b -> b.time <=> a.time }.first()
  }

  private void updateSellOrder(BinanceOrder order, double currentMaxPrice) {
    getApiObject().deleteOrderById(getBinanceSymbol(), order.orderId)

    BinanceOrderPlacement placement = new BinanceOrderPlacement(getBinanceSymbol(), BinanceOrderSide.SELL);
    placement.setType(BinanceOrderType.LIMIT);
    placement.setPrice(BigDecimal.valueOf(currentMaxPrice));
    placement.setQuantity(order.origQty)
    getApiObject().createOrder(placement)
    println 'Updating sell order'
    println 'was : ' + order
    println 'now is : ' + placement
  }

  private void updateBuyOrder(BinanceOrder order, double currentMinPrice) {
    getApiObject().deleteOrderById(getBinanceSymbol(), order.orderId)

    BinanceOrderPlacement placement = new BinanceOrderPlacement(getBinanceSymbol(), BinanceOrderSide.BUY);
    placement.setType(BinanceOrderType.LIMIT);
    placement.setPrice(BigDecimal.valueOf(currentMinPrice));
    placement.setQuantity(order.origQty)
    getApiObject().createOrder(placement)
    println 'Updating buy order'
    println 'was : ' + order
    println 'now is : ' + placement
  }

  public static void main(String[] args) {
    new Main().startMainLoop()
  }

  private void startMainLoop() {
    while (true) {

      pauseExecutionForMillis(2500)
      println 'iterration number : ' + iterationIndex
      Map<String, Double> priceFreeMap = getAvailableBalances(getApiObject())

      double maxPrice = getSellMaxPrice(getApiObject(), getBinanceSymbol(), 20)
      double minPrice = getBuyMinPrice(getApiObject(), getBinanceSymbol(), 20)

      //place sell order using the available balance
      String tradeSellCurrency = 'TRX'
      double tradeSellCurrencyQuantity = getCoinAmount(priceFreeMap, tradeSellCurrency)
      if (shouldSellCoin(tradeSellCurrencyQuantity)) {
        placeSellOrder(maxPrice, (int) tradeSellCurrencyQuantity, getBinanceSymbol())
      }

      //place buy order using the available balance
      String tradeBuyCurrency = 'ETH'
      double tradeBuyCurrencyQuantity = getCoinAmount(priceFreeMap, tradeBuyCurrency)
      if (shouldBuyCoin(tradeBuyCurrencyQuantity, minPrice)) {
        placeBuyOrder(maxPrice, (int) tradeBuyCurrencyQuantity, getBinanceSymbol())
      }

      //get all active orders
      List<BinanceOrder> binanceOrders = getOpenOrdersForSymbol(getBinanceSymbol())

      if (binanceOrders) {
        getSellOrders(binanceOrders).each { order ->
          if (shouldUpdateSellOrder(order, maxPrice)) {
            updateSellOrder(order, maxPrice)
          }
        }

        getBuyOrders(binanceOrders).each { order ->
          if (shouldUpdateBuyOrder(order, minPrice)) {
            updateBuyOrder(order, minPrice)
          }
        }
      }
    }
  }
}
