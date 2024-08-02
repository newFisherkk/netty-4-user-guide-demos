package com.waylau.java.demo.reactor.basic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async Handler
 * 
 * @since 1.0.0 2019年11月14日
 * @author <a href="https://waylau.com">Way Lau</a>
 */
public class AsyncHandler implements Runnable {

	private final Selector selector;

	private final SelectionKey selectionKey;
	private final SocketChannel socketChannel;

	private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
	private ByteBuffer sendBuffer = ByteBuffer.allocate(2048);

	private final static int READ = 0; // 读取就绪
	private final static int SEND = 1; // 响应就绪
	private final static int PROCESSING = 2; // 处理中

	private int status = READ; // 所有连接完成后都是从一个读取动作开始的

	// 开启线程数为4的异步处理线程池
	private static final ExecutorService workers = Executors.newFixedThreadPool(5);

	AsyncHandler(SocketChannel socketChannel, Selector selector) throws IOException {
		this.socketChannel = socketChannel; // 接收客户端连接
		this.socketChannel.configureBlocking(false); // 置为非阻塞模式
		selectionKey = socketChannel.register(selector, SelectionKey.OP_READ); // 将该客户端注册到selector
		selectionKey.attach(this); // 附加处理对象，当前是Handler对象
		this.selector = selector;
	}

	@Override
	public void run() {
		// 如果一个任务正在异步处理，那么这个run是直接不触发任何处理的，
		// read和send只负责简单的数据读取和响应，业务处理完全不阻塞这里的处理
		switch (status) {
		case READ:
			read();
			break;
		case SEND:
			send();
			break;
		default:
		}
	}

	private void read() {
		if (selectionKey.isValid()) {
			try {
				readBuffer.clear();
				String preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
				System.out.println(preFix + "ready to read");
				// read方法结束，意味着本次"读就绪"变为"读完毕"，标记着一次就绪事件的结束
				int count = socketChannel.read(readBuffer);
				if (count > 0) {
					status = PROCESSING; // 置为处理中
					workers.execute(this::readWorker); // 异步处理
				} else {
					// 读模式下拿到的值是-1，说明客户端已经断开连接，那么将对应的selectKey从selector里清除，
					// 否则下次还会select到，因为断开连接意味着读就绪不会变成读完毕，也不cancel，
					// 下次select会不停收到该事件。
					// 所以在这种场景下，需要关闭socketChannel并且取消key，最好是退出当前函数。
					// 注意，这个时候服务端要是继续使用该socketChannel进行读操作的话，
					// 就会抛出“远程主机强迫关闭一个现有的连接”的IO异常。
					selectionKey.cancel();
					socketChannel.close();
					System.out.println("read closed");
				}
			} catch (IOException e) {
				System.err.println("处理read业务时发生异常！异常信息：" + e.getMessage());
				selectionKey.cancel();
				try {
					socketChannel.close();
				} catch (IOException e1) {
					System.err.println("处理read业务关闭通道时发生异常！异常信息：" + e.getMessage());
				}
			}
		}
	}

	void send() {
		if (selectionKey.isValid()) {
			status = PROCESSING; // 置为执行中
			workers.execute(this::sendWorker); // 异步处理
			selectionKey.interestOps(SelectionKey.OP_READ); // 重新设置为读
		}
	}

	// 读入信息后的业务处理
	private void readWorker() {
		String preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
//		try {
//			System.out.println(preFix + " 读阻塞5s");
//			Thread.sleep(5000L);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
		System.out.println(preFix + String.format(" read msg： %s", new String(readBuffer.array(),0, readBuffer.position())));
		status = SEND;
		selectionKey.interestOps(SelectionKey.OP_WRITE); // 注册写事件
		this.selector.wakeup(); // 唤醒阻塞在select的线程
	}

	private void sendWorker() {
		try {
			sendBuffer.clear();
			String msg = String.format("\r\nrecived [%s]", new String(readBuffer.array(),0, readBuffer.position()));
			String preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
			System.out.println(preFix + " send msg: " + msg);
			sendBuffer.put(msg.getBytes());
			sendBuffer.flip();
			// write方法结束，意味着本次写就绪变为写完毕，标记着一次事件的结束，write前每次select都会获取到写事件
			int count = socketChannel.write(sendBuffer);
			if (count < 0) {
				// 同上，write场景下，取到-1，也意味着客户端断开连接
				selectionKey.cancel();
				socketChannel.close();
				System.out.println("send close");
			}
			// 注意write后仍然会获取到写事件？下面找到解释
			// OP_WRITE事件的就绪条件并不是发生在调用channel的write方法之后，
			// 而是在当底层缓冲区有空闲空间的情况下。因为写缓冲区在绝大部分时候都是有空闲空间的，
			// 所以如果你注册了写事件，这会使得写事件一直处于就就绪，选择处理现场就会一直占用着CPU资源。
			// 所以，只有当你确实有数据要写时再注册写操作，并在写完以后马上取消注册
			//在大部分情况下，我们直接调用channel的write方法写数据就好了，没必要都用OP_WRITE事件。那么OP_WRITE事件主要是在什么情况下使用的了？
			//其实OP_WRITE事件主要是在发送缓冲区空间满的情况下使用的

			// 没断开连接，则再次切换到读
			status = READ;
		} catch (IOException e) {
			System.err.println("异步处理send业务时发生异常！异常信息：" + e.getMessage());
			selectionKey.cancel();
			try {
				socketChannel.close();
			} catch (IOException e1) {
				System.err.println("异步处理send业务关闭通道时发生异常！异常信息：" + e.getMessage());
			}
		}
	}
}
