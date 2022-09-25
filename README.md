# Trade Engine Practice
**_A side project using spring boot_**

You can execute service by Docker and download [Postman Collection Json file](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/resources/Trade%20Engine.postman_collection.json) to create order through HTTP protocol.
```sh
docker pull rhrmgwgeil/trade-engine:latest
```

## Introduction 
There's no database, cache server, and message queue in the project. I used a class [DataStorage.class](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/java/com/example/tradeEngine/common/DataStorage.java) to replace it. As you can see in the file, I separated the buy order and sell order into two **HashMap** and used order price as key and a **TreeSet** as value. The **TreeSet** contains order information and sort ascending by order's **createTime**. To achieve thread safe, I used [CacheDistributeLock.class](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/java/com/example/tradeEngine/common/CacheDistributeLock.java) for the buy and sell queue.

The **PROCESSING_TASK_QUEUE** is used to trigger the match engine. It contains all order types. The Trade Engine will pull the order from it and execute matching processing. When the order is filled it won't push it back to the message queue.

The **FILLED_TASK_QUEUE** is used to update the database. When the order is filled it will be pushed to this queue and wait for execution.

## Data Schema

#### [OrderPojo](https://github.com/rhrmgwgeil/tradeEngine/blob/927fef323844238b41de953d61de0813551ead39/src/main/java/com/example/tradeEngine/pojo/OrderPojo.java)
| Type                  | Column Name          | Description |
|-----------------------|----------------------|-------------|
| UUID                  | id                   |Use to identify each orderPojo             |
| LocalDateTime         | createTime           |Timestamp without time zone             |
| LocalDateTime         | updateTime           |Timestamp without time zone             |
| [OrderType](https://github.com/rhrmgwgeil/tradeEngine/blob/927fef323844238b41de953d61de0813551ead39/src/main/java/com/example/tradeEngine/common/OrderType.java)             | orderType            |0:BUY, 1:SELL              |
| [PriceType](https://github.com/rhrmgwgeil/tradeEngine/blob/927fef323844238b41de953d61de0813551ead39/src/main/java/com/example/tradeEngine/common/PriceType.java)             | priceType            |0:MARKET, 1:LIMIT             |
| BigDecimal            | price                |Round to two decimal places             |
| Long                  | quantity             |Order quantity             |
| [OrderStatus](https://github.com/rhrmgwgeil/tradeEngine/blob/927fef323844238b41de953d61de0813551ead39/src/main/java/com/example/tradeEngine/common/OrderStatus.java)           | orderStatus          |0:PROCESSING, 1:FILLED, 2:KILLED             |
| List\<[OrderDetailPojo]()> | matchOrderDetailPojo |Match detail of order.             |

### [OrderDetailPojo]()
| Type          | Column Name | Description |
|---------------|-------------|-------------|
| UUID          | id          |Use to identify each orderPojo             |
| LocalDateTime | updateTime  |Timestamp without time zone, Order match time             |
| BigDecimal    | price       |Round to two decimal places, Match price             |
| Long          | quantity    |Macth quantity             |

## Engine Logic
![Trade Engine Sequence Diagram](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/Trade%20Engine%20Sequence%20Diagram.png?raw=true "Trade Engine Sequence Diagram")
