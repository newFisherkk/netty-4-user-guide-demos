package com.waylau.java.demo.chat;

import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * 服务端实现开发步骤
 * 1、创建选择器
 * 2、创建服务端 ServerSocketChannel
 * 3、创建服务端的监听端口
 * 4、设置服务端的 ServerSocketChannel 是非阻塞的
 * 5、将服务端的 ServerSocketChannel 注册到 Selector 选择器上面,并将事件设置成连接事件
 * 6、监听就绪事件
 * 7、根据相应的就绪事件进行逻辑处理【连接事件、可读事件、可写事件等等】
 * 8、如果有客户端发来消息的时候，那么需要将客户端发来的消息发送给其他客户端
 */
public class ChatServer {
    private Selector selector;
    private ServerSocketChannel listenerChannel;
    private static final int PORT = 8080;

    public ChatServer() {
        init();
    }

    /**
     * 服务端初始化方法
     */
    private void init() {
        try {
            selector = Selector.open();
            listenerChannel = ServerSocketChannel.open();
            // 绑定端口
            listenerChannel.bind(new InetSocketAddress(PORT));
            // 设置非阻塞模式
            listenerChannel.configureBlocking(false);
            // 将 listenerChannel 注册到 selector，事件为：OP_ACCEPT
            listenerChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("服务端初始化完毕......");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理客户端的监听事件
     */
    public void listenEvent() {
        try {
            while (true) {
                int selected = selector.select();
                if (selected > 0) { // 如果返回值大于 0 说明有事件处理
                    // 遍历 SelectionKey 集合进行处理，获取就绪事件的集合
                    Iterator<SelectionKey> iterator =
                            selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        // 根据不同的就绪事件做不同的处理
                        if (key.isAcceptable()) {// 处理客户端连接事件
                            HandleAcceptEvent();
                        }
                        if (key.isReadable()) { // 处理通道的读事件
                            // 处理读事件读取客户端消息
                            handleReadEvent(key);
                        }
                        // 将当前的 selectionKey 删除，防止重复处理
                        iterator.remove();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理客户端的连接事件
     *
     * @throws IOException
     */
    private void HandleAcceptEvent() throws IOException {
        SocketChannel socketChannel = listenerChannel.accept();
        socketChannel.configureBlocking(false);// 设置非阻塞
        // 注册到 selector 选择器上面
        socketChannel.register(selector, SelectionKey.OP_READ);
        System.out.println(socketChannel.getRemoteAddress() + " 上线了。。。");
    }

    /**
     * 处理客户端发来消息的读事件
     *
     * @param key
     */
    private void handleReadEvent(SelectionKey key) {
        SocketChannel channel = null;
        try {
            // 获取 SocketChannel 通道信息
            channel = (SocketChannel) key.channel();
            // 创建 Buffer 缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            // 通过将socketChannel 将数据读入到 buffer缓冲区
            int selected = channel.read(buffer);
            if (selected > 0) {
                String msg = new String(buffer.array(), CharsetUtil.UTF_8);
                // 向其它客户端转发消息，但是需要将自己排出出去
                sendMsgToOtherClient(msg, channel);
            }
        } catch (IOException e) {// 如果捕获到异常就说明客户端下线了
            try {
                System.out.println(channel.getRemoteAddress() + "离线了。。。");
                // 取消注册
                key.cancel();
                // 关闭通道
                channel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

    }

    /**
     * 转发消息给其它客户端，但是需要将自己和ServerSocketChannel排除掉
     *
     * @param msg
     * @param self
     */
    private void sendMsgToOtherClient(String msg, SocketChannel self) {
        // 遍历所有注册到 selector 上的 其他SocketChannel通道 并排除自己的通道
        // 通过 selector.keys() 获取注册到selector上面的所有通道信息
        for (SelectionKey key : selector.keys()) {
            // 通过 key 取出对应的 SocketChannel,因为ServerSocketChannel
            // 也注册到了selelcor上面
            // 并且排除自己的通道
            if (key.channel() instanceof SocketChannel && key.channel() != self) {
                try { // 将buffer的数据写入通道，并转发给其他通道
                    ((SocketChannel) key.channel())
                            .write(ByteBuffer.wrap(msg.getBytes()));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.listenEvent();
    }
}

