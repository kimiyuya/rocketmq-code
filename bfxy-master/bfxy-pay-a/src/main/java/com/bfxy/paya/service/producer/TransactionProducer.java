package com.bfxy.paya.service.producer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TransactionProducer implements InitializingBean {

	private TransactionMQProducer producer;
	
	private ExecutorService executorService;

	//监听器对象
	@Autowired
	private TransactionListenerImpl transactionListenerImpl;
	
	private static final String NAMESERVER = "192.168.11.121:9876;192.168.11.122:9876;192.168.11.123:9876;192.168.11.124:9876";
	
	private static final String PRODUCER_GROUP_NAME = "tx_pay_producer_group_name";
	
	private TransactionProducer() {
		//创建生产者
		this.producer = new TransactionMQProducer(PRODUCER_GROUP_NAME);
		//初始化线程池
		// 由于本地回调监听跟消息的发送会并发进行，所以可以使用线程池来执行操作
		this.executorService = new ThreadPoolExecutor(2, 5, 100, TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(2000), new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						Thread thread = new Thread(r);
						thread.setName(PRODUCER_GROUP_NAME + "-check-thread");
						return thread;
					}
				});
		this.producer.setExecutorService(executorService);
		this.producer.setNamesrvAddr(NAMESERVER);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		//注册监听器
		this.producer.setTransactionListener(transactionListenerImpl);
		//启动生产者
		start();
	}

	private void start() {
		try {
			this.producer.start();
		} catch (MQClientException e) {
			e.printStackTrace();
		}
	}
	
	public void shutdown() {
		this.producer.shutdown();
	}
	
	public TransactionSendResult sendMessage(Message message, Object argument) {
		TransactionSendResult sendResult = null;
		try {
			//发送消息
			sendResult = this.producer.sendMessageInTransaction(message, argument);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sendResult;
	}
	
	
	
	
	
	
	
	
	
	
	
}
