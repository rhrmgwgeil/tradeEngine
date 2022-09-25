# Trade Engine Practice
**_A side project using spring boot_**

You can execute service by Docker and download [Postman Collection Json file](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/resources/Trade%20Engine.postman_collection.json) to create order through HTPP protocol.
```sh
docker pull rhrmgwgeil/trade-engine:latest
```

## Engine Logic


There's no database, cache server, and message queue in the project. I used a class [DataStorage.class](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/java/com/example/tradeEngine/common/DataStorage.java) to replace it. As you can see in the file, I separated the buy order and sell order into two **HashMap** and used order price as key and a **TreeSet** as value. The **TreeSet** contains order information and sort ascending by order's **createTime**. To achieve thread safe, I used [CacheDistributeLock.class](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/src/main/java/com/example/tradeEngine/common/CacheDistributeLock.java) for the buy and sell queue.

The **PROCESSING_TASK_QUEUE** is used to trigger the match engine. It contains all order types. As a message queue, we can pull the order from it and start the matching processing. When the order is filled it won't push it back to the message queue.

![Trade Engine Sequence Diagram](https://github.com/rhrmgwgeil/tradeEngine/blob/5864775552f385d8780e0f7f680210ade4a0e63e/Trade%20Engine%20Sequence%20Diagram.png?raw=true "Trade Engine Sequence Diagram")
