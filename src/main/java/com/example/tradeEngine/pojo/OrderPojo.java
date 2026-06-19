package com.example.tradeEngine.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.tradeEngine.common.OrderStatus;
import com.example.tradeEngine.common.OrderType;
import com.example.tradeEngine.common.PriceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPojo implements Serializable, Comparable<OrderPojo> {

	private static final long serialVersionUID = -3176473426588020408L;

	private UUID id;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private OrderType orderType;
	private PriceType priceType;
	private BigDecimal price;
	private Long quantity;
	private OrderStatus orderStatus;
	private List<OrderDetailPojo> matchOrderDetailPojo = new ArrayList<OrderDetailPojo>();

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public void setOrderType(OrderType orderType) {
		this.orderType = orderType;
	}

	public PriceType getPriceType() {
		return priceType;
	}

	public void setPriceType(PriceType priceType) {
		this.priceType = priceType;
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

	public OrderStatus getOrderStatus() {
		return orderStatus;
	}

	public void setOrderStatus(OrderStatus orderStatus) {
		this.orderStatus = orderStatus;
	}

	public List<OrderDetailPojo> getMatchOrderDetailPojo() {
		return matchOrderDetailPojo;
	}

	public void setMatchOrderDetailPojo(List<OrderDetailPojo> matchOrderDetailPojo) {
		this.matchOrderDetailPojo = matchOrderDetailPojo;
	}

	public BigDecimal getPriceKey() {
		if (null != this.price) {
			return this.price.setScale(2, RoundingMode.HALF_UP);
		} else {
			return null;
		}
	}

	public OrderPojo() {
		super();
	}

	public OrderPojo(UUID id, LocalDateTime createTime, LocalDateTime updateTime, OrderType orderType,
			PriceType priceType, BigDecimal price, Long quantity, OrderStatus orderStatus,
			List<OrderDetailPojo> matchOrderDetailPojo) {
		this.id = id;
		this.createTime = createTime;
		this.updateTime = updateTime;
		this.orderType = orderType;
		this.priceType = priceType;
		this.price = price;
		this.quantity = quantity;
		this.orderStatus = orderStatus;
		this.matchOrderDetailPojo = matchOrderDetailPojo;
	}

	@Override
	public int compareTo(OrderPojo other) {
		int timeCompare = this.createTime.compareTo(other.createTime);
		// if time is same, compare UUID to prevent duplicate
		return (timeCompare != 0) ? timeCompare : this.id.compareTo(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		OrderPojo orderPojo = (OrderPojo) o;
		// If UUID is the same, it is the same order
		return Objects.equals(id, orderPojo.id);
	}

	@Override
	public Object clone() {
		OrderPojo orderPojo = null;
		try {
			orderPojo = (OrderPojo) super.clone();
		} catch (CloneNotSupportedException e) {
			List<OrderDetailPojo> cloneList = this.getMatchOrderDetailPojo().stream()
					.map(detail -> (OrderDetailPojo) detail.clone()).collect(Collectors.toList());

			orderPojo = new OrderPojo(
					this.getId(),
					this.getCreateTime(),
					this.getUpdateTime(),
					this.getOrderType(),
					this.getPriceType(),
					this.getPrice(),
					this.getQuantity(),
					this.getOrderStatus(),
					cloneList);
		}
		return orderPojo;
	}

}
