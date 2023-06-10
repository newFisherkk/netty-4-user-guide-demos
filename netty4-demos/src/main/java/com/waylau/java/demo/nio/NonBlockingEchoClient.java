/**
 * Welcome to https://waylau.com
 */
package com.waylau.java.demo.nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Non Blocking Echo Client.
 * 
 * @since 1.0.0 2019年9月28日
 * @author <a href="https://waylau.com">Way Lau</a>
 */
public class NonBlockingEchoClient {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("用法: java NonBlockingEchoClient <host name> <port number>");
			System.exit(1);
		}

		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);

		SocketChannel socketChannel = null;
		try {
			socketChannel = SocketChannel.open();
			socketChannel.connect(new InetSocketAddress(hostName, portNumber));
		} catch (IOException e) {
			System.err.println("NonBlockingEchoClient异常： " + e.getMessage());
			System.exit(1);
		}
		//写缓冲区
		ByteBuffer writeBuffer = ByteBuffer.allocate(1024 * 1);
		//读缓冲区
		ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1);

		try (BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {
			String userInput;
			while ((userInput = stdIn.readLine()) != null) {
				//写入到写缓冲区
				writeBuffer.put(userInput.getBytes());
				//切换成读模式
				writeBuffer.flip();
				// 创建一个limit()大小的字节数组(因为就只有limit这么多个数据可读)
				byte[] bytes = new byte[writeBuffer.limit()];
				// 将读取的数据装进我们的字节数组中
				writeBuffer.get(bytes);
				System.out.println("准备向服务端发送: " + new String(bytes));
				//将 position设置为0， 所以你可以重读 Buffer 中的所有数据。limit 保持不变，仍然表示能从 Buffer 中读取到多少个元素
				writeBuffer.rewind();

				// 写消息到管道，相当于读取writeBuffer,所以写消息前要flip
				int write = socketChannel.write(writeBuffer);
				// 清理缓冲区,相当于切换成写模式
				writeBuffer.clear();

				// 管道读消息，如果服务端没返回消息，则一直阻塞
				int read = socketChannel.read(readBuffer);
				readBuffer.flip();
				// 创建一个limit()大小的字节数组(因为就只有limit这么多个数据可读)
				bytes = new byte[readBuffer.limit()];
				// 将读取的数据装进我们的字节数组中
				readBuffer.get(bytes);
				System.out.println("服务端返回: " + new String(bytes));
				readBuffer.clear();
			}
		} catch (UnknownHostException e) {
			System.err.println("不明主机，主机名为： " + hostName);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("不能从主机中获取I/O，主机名为：" + hostName);
			System.exit(1);
		}
	}

}
