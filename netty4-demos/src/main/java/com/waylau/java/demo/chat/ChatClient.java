package com.waylau.java.demo.chat;

import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;

/**
 * 客户端开发步骤
 * 1、和服务端一样第一步也是创建选择器
 * 2、创建客户端 SocketChannel
 * 3、设置非阻塞通道SocketChanel
 * 4、将客户端的 SocketChannel 注册到 Selector 选择器上面,并将事件设置成监听已读的就绪事件
 * 5、根据相应的就绪事件进行逻辑处理【连接事件、可读事件、可写事件等等】
 * 6、如果有服务端发来消息的时候，那么需要将其他客户端发来的消息进行对应处理
 */
public class ChatClient {
    private Selector selector;
    private SocketChannel socketChannel;
    private String username;

    public ChatClient() {
        init();
    }

    /**
     * 客户端初始化操作
     */
    private void init() {
        try {
            selector = Selector.open();
            socketChannel = socketChannel
                    .open(new InetSocketAddress("127.0.0.1", 8080));
            socketChannel.configureBlocking(false);
            // 将channel注册到selector
            socketChannel.register(selector, SelectionKey.OP_READ);
            // 获取 username 信息
            username = socketChannel.getLocalAddress().toString().substring(1);
            System.out.println("客户端" + username + " 初始化完成 ...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向服务端发送消息
     *
     * @param info
     */
    public void sendInfoToServer(String info) {
        info = username + "说：" + info;
        try {
            socketChannel.write(ByteBuffer.wrap(info.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取从服务器端回复的消息
     */
    public void receiveInfoFromServer() {
        while (true) {
            try {
                int selected = selector.select();
                if (selected > 0) {
                    Iterator<SelectionKey> iterator = selector
                            .selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        if (key.isReadable()) {
                            // 获取socketChannel 通道信息
                            SocketChannel socketChannel =
                                    (SocketChannel) key.channel();
                            // 创建一个buffer缓冲区用来接收消息
                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            // 将通道中的数据读取到缓冲区
                            int count = socketChannel.read(buffer);
                            if (count > 0) {
                                System.out.println(
                                        new String(buffer.array(), CharsetUtil.UTF_8));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient();
        // 启动一个线程，监听读取从服务器端发送的数据
        new Thread() {
            @Override
            public void run() {
                chatClient.receiveInfoFromServer();
            }
        }.start();
        // 发送数据给服务器端
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String msg = sc.nextLine();
            chatClient.sendInfoToServer(msg);
        }
    }
}