package com.bfxy.paya.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bfxy.paya.entity.CustomerAccount;
import com.bfxy.paya.mapper.CustomerAccountMapper;
import com.bfxy.paya.service.PayService;
import com.bfxy.paya.service.producer.CallbackService;
import com.bfxy.paya.service.producer.TransactionProducer;
import com.bfxy.paya.utils.FastJsonConvertUtil;

@Service
public class PayServiceImpl implements PayService {

	public static final String TX_PAY_TOPIC = "tx_pay_topic";
	
	public static final String TX_PAY_TAGS = "pay";
	
	@Autowired
	private CustomerAccountMapper customerAccountMapper;
	
	@Autowired
	private TransactionProducer transactionProducer;
	
	@Autowired
	private CallbackService callbackService;
	
	@Override
	public String payment(String userId, String orderId, String accountId, double money) {
		String paymentRet = "";
		try {
			//	最开始有一步 token验证操作（重复提单问题）
			
			BigDecimal payMoney = new BigDecimal(money);
			
			//加锁开始（获取）
			
			CustomerAccount old = customerAccountMapper.selectByPrimaryKey(accountId);
			BigDecimal currentBalance = old.getCurrentBalance();
			int currentVersion = old.getVersion();
			//	要对大概率事件进行提前预判（小概率事件我们做放过,但是最后保障数据的一致性即可）
			//业务出发:
			//当前一个用户账户 只允许一个线程（一个应用端访问）
			//技术出发：
			//1 redis去重 分布式锁
			//2 数据库乐观锁去重
			//	做扣款操作的时候：获得分布式锁，看一下能否获得
			BigDecimal newBalance = currentBalance.subtract(payMoney);
			
			//加锁结束（释放）

			//加这个分布式锁的目的是，提前查出余额是否足够，如果足够才进行消息的投递，不够的话直接不发了，返回余额不足。
			//这样可以节省整个应用系统的性能。不然的话要是所有余额不足都还能进行消息的投递，那之后还要进行一系列回滚操作，性能很差。
			
			if(newBalance.doubleValue() > 0 ) {	//	或者一种情况获取锁失败
				//	1.组装消息
				//  1.执行本地事务
				String keys = UUID.randomUUID().toString() + "$" + System.currentTimeMillis();
				Map<String, Object> params = new HashMap<>();
				params.put("userId", userId);
				params.put("orderId", orderId);
				params.put("accountId", accountId);
				params.put("money", money);	//100
				
				Message message = new Message(TX_PAY_TOPIC, TX_PAY_TAGS, keys, FastJsonConvertUtil.convertObjectToJSON(params).getBytes());
				//	可能需要用到的参数
				params.put("payMoney", payMoney);
				params.put("newBalance", newBalance);
				params.put("currentVersion", currentVersion);
				
				//	同步阻塞
				CountDownLatch countDownLatch = new CountDownLatch(1);
				params.put("currentCountDown", countDownLatch);


				//	消息发送并且 本地的事务执行
				TransactionSendResult sendResult = transactionProducer.sendMessage(message, params);

				//等待本地事务线程完成。才能放行，进行以下操作
				countDownLatch.await();

				//必须是消息发送成功，并且本地事务执行成功且commit状态，才能算是成功。然后mq会保证消息发送了一定会被消费，如果消费者没有消费这条消息，
				// 那么broker会重复投递该消息16次，若仍然不成功则需要人工介入。这就是保证最终一致性

				//那么如何感知本地事务的状态呢？因为我们发消息和本地事务是同步进行的，所以可能主线程判断消息发送状态为OK时，本地事务线程还没完成，这就需要对主线程进行同步阻塞。
				//可以用countdownlatch完成。
				if(sendResult.getSendStatus() == SendStatus.SEND_OK 
						&& sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
					//	回调order通知支付成功消息
					callbackService.sendOKMessage(orderId, userId);
					paymentRet = "支付成功!";
				} else {
					paymentRet = "支付失败!";
				}
			} else {
				paymentRet = "余额不足!";
			}
		} catch (Exception e) {
			e.printStackTrace();
			paymentRet = "支付失败!";
		}
		return paymentRet;
	}

}
