package com.yoho.erp.sync.inventory.util;

import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 接收到系统关闭的处理
 */
public final class QueueClosingHandler implements SignalHandler {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("common");

	private BlockingQueue<?> elementQueue;

	private volatile AtomicBoolean producerIsRunning, producerIsStop, consumerIsRunning, consumerIsFinish;

	public QueueClosingHandler(BlockingQueue<?> elementQueue, AtomicBoolean producerIsRunning, AtomicBoolean producerIsStop,
							   AtomicBoolean consumerIsRunning, AtomicBoolean consumerIsFinish) {
		this.elementQueue = elementQueue;
		this.producerIsRunning = producerIsRunning;
		this.producerIsStop = producerIsStop;
		this.consumerIsRunning = consumerIsRunning;
		this.consumerIsFinish = consumerIsFinish;
	}

	/**
	 * 注册关闭钩子
	 * 处理系统的关闭信号
	 * Windows支持Ctrl+C | kill -1 | kill -2
	 * Linux支持Ctrl+C | kill -1 | kill -2 | kill -6 | kill -14 | kill -15
	 */
	public void registerHook() {
		Signal.handle(new Signal("INT"), this); // Ctrl+C
		Signal.handle(new Signal("ABRT"), this); // kill -6
		Signal.handle(new Signal("TERM"), this); // kill -15

		String osName = System.getProperty("os.name");
		if (osName != null && !osName.toUpperCase().contains("WINDOWS")) {
			Signal.handle(new Signal("HUP"), this); // kill -1
			// Signal.handle(new Signal("QUIT"), signalHandler); // kill -3 already used by VM or OS: SIGQUIT
			// Signal.handle(new Signal("KILL"), signalHandler); // kill -9 already used by VM or OS: SIGKILL
			// Signal.handle(new Signal("USR1"), new ClosingHandler("kill -10")); // kill -10 already used by VM or OS: SIGUSR1
			// Signal.handle(new Signal("USR2"), new ClosingHandler("kill -12")); // kill -12 already used by VM or OS: SIGUSR2
			Signal.handle(new Signal("ALRM"), this); // kill -14
		}
	}

	@Override
	public void handle(Signal signal) {

		producerIsRunning.set(false);

		LOGGER.info("receive command {}, program is stopping...", signal.getName());

		while (!producerIsStop.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
			LOGGER.info("waiting for producer stop");
		}

		while (!elementQueue.isEmpty()) { // 当数据队列还有数据时 要等待队列消费完
			LOGGER.info("{} records to progress", elementQueue.size());
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		consumerIsRunning.set(false); // 消费完成后用这个变量控制数据的消费者停止消费

		while (!consumerIsFinish.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
			LOGGER.info("waiting for consumer stop");
		}

		RedisUtils.closePool();
		MongoUtils.closePool();

		// Logger.loggerIsRunning = false;

		LOGGER.info("program is finish, exit");
	}
}