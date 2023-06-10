/**
 * Welcome to https://waylau.com
 */
package com.waylau.java.demo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Non Bloking Echo Server.
 * 
 * @since 1.0.0 2019年9月28日
 * @author <a href="https://waylau.com">Way Lau</a>
 */
public class NonBlokingEchoServer {
	public static int DEFAULT_PORT = 7;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int port;

		try {
			port = Integer.parseInt(args[0]);
		} catch (RuntimeException ex) {
			port = DEFAULT_PORT;
		}

		ServerSocketChannel serverChannel;
		Selector selector;
		try {
			serverChannel = ServerSocketChannel.open();
			InetSocketAddress address = new InetSocketAddress(port);
			serverChannel.bind(address);
			serverChannel.configureBlocking(false);
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);

			System.out.println("NonBlokingEchoServer已启动，端口：" + port);
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}

		while (true) {
			try {
				selector.select();
			} catch (IOException e) {
				System.out.println("NonBlockingEchoServer异常!" + e.getMessage());
			}
			Set<SelectionKey> readyKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				try {
					// 可连接
					if (key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						SocketChannel socketChannel = server.accept();

						System.out.println("NonBlokingEchoServer接受客户端的连接：" + socketChannel);

						// 设置为非阻塞
						socketChannel.configureBlocking(false);

						// 客户端注册到Selector
						SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_READ);

						// 分配缓存区 使用的是JVM的堆内存，对于JVM来说，分配比较快，但是读写比较慢，因为需要将操作系统内存里的数据复制到JVM内存
						ByteBuffer buffer = ByteBuffer.allocate(1024 * 2);
						//使用的是操作系统级别的内存，分配比较慢，但是数据的读写比较快，因为少了一次从系统内存到JVM内存的复制过程
//						ByteBuffer.allocateDirect(1024 * 4);
						clientKey.attach(buffer);
					}

					// 可读
					if (key.isReadable()) {
						SocketChannel client = (SocketChannel) key.channel();
						ByteBuffer output = (ByteBuffer) key.attachment();
						//读取数据写入到到缓冲区
						int read = client.read(output);
						//切换为读模式
						output.flip();
						// 创建一个limit()大小的字节数组(因为就只有limit这么多个数据可读)
						byte[] bytes = new byte[output.limit()];
						//读完之后，缓冲区的postion已经到了limit位置，无法再次写入，需要标记下postion位置，也就是初始位置0
//						output.mark();
						// 将读取的数据装进我们的字节数组中
						output.get(bytes);
						// 输出数据
						System.out.println("接受客户端信息:" + new String(bytes));
//						output.reset();
						//也可以不加mark，读完之后rewind，然后再compact
						output.rewind();
						output.compact();
						key.interestOps(SelectionKey.OP_WRITE);
					}

					// 可写
					if (key.isWritable()) {
						SocketChannel client = (SocketChannel) key.channel();
						ByteBuffer output = (ByteBuffer) key.attachment();
						output.put("[服务端写入]".getBytes());
						//写模式转换为读模式
						output.flip();
						//读取缓冲区数据写入到客户端管道
						int write = client.write(output);
						//拷贝未读取的数据到缓冲区最前面， pos = limit -pos； limit = cap;
						output.compact();

						key.interestOps(SelectionKey.OP_READ);
					}
				} catch (IOException ex) {
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException cex) {
						System.out.println(
								"NonBlockingEchoServer异常!" + cex.getMessage());
					}
				}
			}
		}
	}

}
