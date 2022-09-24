package com.example.tradeEngine.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class OrderDetailPojo implements Serializable {
	private static final long serialVersionUID = 403866035190790954L;
	
	private UUID id;
	private LocalDateTime updateTime;
	private BigDecimal price;
	private Long quantity;
	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}
	public LocalDateTime getUpdateTime() {
		return updateTime;
	}
	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}
	public BigDecimal getPrice() {
		return price;
	}
	public void setPrice(BigDecimal price) {
		this.price = price;
	}
	public Long getQuantity() {
		return quantity;
	}
	public void setQuantity(Long quantity) {
		this.quantity = quantity;
	}
	
	public OrderDetailPojo() {
		super();
	}
	
	public OrderDetailPojo(UUID id, LocalDateTime updateTime, BigDecimal price, Long quantity) {
		super();
		this.id = id;
		this.updateTime = updateTime;
		this.price = price;
		this.quantity = quantity;
	}
	
	public OrderDetailPojo(OrderPojo orderPojo) {
		super();
		this.id = orderPojo.getId();
		this.updateTime = orderPojo.getUpdateTime();
		this.price = orderPojo.getPrice();
		this.quantity = orderPojo.getQuantity();
	}
	
	@Override
	public Object clone(){
		OrderDetailPojo orderDetailPojo = null;
	    try {
	    	orderDetailPojo = (OrderDetailPojo) super.clone();
	    } catch (CloneNotSupportedException e) {
	    	orderDetailPojo = new OrderDetailPojo(
	          this.getId(), 
	          this.getUpdateTime(),
	          this.getPrice(),
	          this.getQuantity());
	    }
		return orderDetailPojo;
	}
	
}
