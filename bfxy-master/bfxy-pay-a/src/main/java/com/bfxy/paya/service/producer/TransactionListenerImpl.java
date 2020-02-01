package com.bfxy.paya.service.producer;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bfxy.paya.mapper.CustomerAccountMapper;

//TransactionListener的实现类
//自定义线程池检查请求
@Component
public class TransactionListenerImpl implements TransactionListener {

	@Autowired
	private CustomerAccountMapper customerAccountMapper;

	//回调函数：一旦生产者发送事务消息成功，会调用这个方法提交本地事务
	/**
	 * send()
	 * @param msg send中的message对象，
	 * @param arg send方法中回调函数之后的传入的参数
	 * @return
	 */
	@Override
	public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
		System.err.println("执行本地事务单元------------");
		CountDownLatch currentCountDown = null;
		try {
			Map<String, Object> params = (Map<String, Object>) arg;
			String userId = (String)params.get("userId");
			String accountId = (String)params.get("accountId");
			String orderId = (String)params.get("orderId");
			BigDecimal payMoney = (BigDecimal)params.get("payMoney");	//	当前的支付款
			BigDecimal newBalance = (BigDecimal)params.get("newBalance");	//	前置扣款成功的余额
			int currentVersion = (int)params.get("currentVersion");
			currentCountDown = (CountDownLatch)params.get("currentCountDown");
		
			//updateBalance 传递当前的支付款 数据库操作（数据库乐观锁实现）:
			Date currentTime = new Date();
			int count = this.customerAccountMapper.updateBalance(accountId, newBalance, currentVersion, currentTime);
			//count == 1 表示更新成功， else 表示不成功
			if(count == 1) {
				currentCountDown.countDown();
				return LocalTransactionState.COMMIT_MESSAGE;
			} else {
				//更新失败，设置本地事务状态为失败，之前发送的消息等于白发了，废弃掉，等待rocketMQ进行清理。
				currentCountDown.countDown();
				return LocalTransactionState.ROLLBACK_MESSAGE;
			}
		} catch (Exception e) {
			e.printStackTrace();
			currentCountDown.countDown();
			return LocalTransactionState.ROLLBACK_MESSAGE;
		}
		
	}

	//当prepare发送后超时没有返回，那么MQ服务器会回调执行这个方法用来检查上次本地事务执行的状态。
	//这里需要更新本地事物的最终状态
	@Override
	public LocalTransactionState checkLocalTransaction(MessageExt msg) {
		// TODO Auto-generated method stub
//		Integer status = localTrans.get(msg.getTransactionId());
//		if (null != status) {
//			switch (status) {
//				case 0:
//					return LocalTransactionState.UNKNOW;
//				case 1:
//					return LocalTransactionState.COMMIT_MESSAGE;
//				case 2:
//					return LocalTransactionState.ROLLBACK_MESSAGE;
//				default:
//					return LocalTransactionState.COMMIT_MESSAGE;
//			}
//		}
//		return LocalTransactionState.COMMIT_MESSAGE;
		return null;
	}

}
