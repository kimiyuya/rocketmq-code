package com.bfxy.pkg.service.consumer;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.stereotype.Component;

import com.bfxy.pkg.utils.FastJsonConvertUtil;

@Component
public class PkgOrderlyConsumer {

	private DefaultMQPushConsumer consumer;
	
	public static final String PKG_TOPIC = "pkg_topic";
	
	public static final String PKG_TAGS = "pkg";
	
	public static final String NAMESERVER = "192.168.11.121:9876;192.168.11.122:9876;192.168.11.123:9876;192.168.11.124:9876";
	
	public static final String CONSUMER_GROUP_NAME = "orderly_consumer_group_name"; 
	
	
	private PkgOrderlyConsumer() throws MQClientException {
		this.consumer = new DefaultMQPushConsumer(CONSUMER_GROUP_NAME);
		this.consumer.setConsumeThreadMin(10);
		this.consumer.setConsumeThreadMin(30);
		this.consumer.setNamesrvAddr(NAMESERVER);
		this.consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
		this.consumer.subscribe(PKG_TOPIC, PKG_TAGS);
		this.consumer.setMessageListener(new PkgOrderlyListener());
		consumer.setMessageModel(MessageModel.BROADCASTING);
		this.consumer.start();
	}
	
	class PkgOrderlyListener implements MessageListenerOrderly {
		
		Random random = new Random();
		
		@Override
		public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
			
			for(MessageExt msg: msgs) {
				try {
					String topic = msg.getTopic();
					String msgBody = new String(msg.getBody(), "utf-8");
					String tags = msg.getTags();
					String keys = msg.getKeys();	
					System.err.println("收到消息：" + "  topic :" + topic + "  ,tags : " + tags + "keys :" + keys + ", msg : " + msgBody);
					
					Map<String, Object> body = FastJsonConvertUtil.convertJSONToObject(msgBody, Map.class);
					String orderId = (String) body.get("orderId");
					String userId = (String) body.get("userId");
					String text = (String)body.get("text");
					
					//	模拟实际的业务耗时操作
					//	PS: 创建包裹信息  、对物流的服务调用（异步调用）
					TimeUnit.SECONDS.sleep(random.nextInt(3) + 1);
					
					System.err.println("业务操作: " + text);
					
				} catch (Exception e) {
					e.printStackTrace();
					//在并发消费场景时，如果消费失败，Consumer会将消费失败消息发回到 Broker重试队列，跳过当前消息，等待下次拉取该消息再进行消费。
					//
					//但是在完全严格顺序消费消费时，这样做显然不行。也因此，消费失败的消息，会挂起队列一会会，稍后继续消费。
					//
					//不过消费失败的消息一直失败，也不可能一直消费。当超过消费重试上限时，Consumer会将消费失败超过上限的消息发回到Broker死信队列。
					return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
				}
			}
			
			return ConsumeOrderlyStatus.SUCCESS;
		}
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
