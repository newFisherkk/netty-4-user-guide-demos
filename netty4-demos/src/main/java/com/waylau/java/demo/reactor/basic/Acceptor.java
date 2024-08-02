package com.waylau.java.demo.reactor.basic;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Acceptor
 * 
 * @since 1.0.0 2019年11月14日
 * @author <a href="https://waylau.com">Way Lau</a>
 */
public class Acceptor implements Runnable {

	private final Selector selector;

	private final ServerSocketChannel serverSocketChannel;

	Acceptor(ServerSocketChannel serverSocketChannel, Selector selector) {
		this.serverSocketChannel = serverSocketChannel;
		this.selector = selector;
	}

	@Override
	public void run() {
		SocketChannel socketChannel;
		try {
			socketChannel = serverSocketChannel.accept();
			if (socketChannel != null) {
				String preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
				System.out.println(preFix + String.format(" accpet %s", socketChannel.getRemoteAddress()));

				// 这里把客户端通道传给Handler，Handler负责接下来的事件处理
				new AsyncHandler(socketChannel, selector);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
