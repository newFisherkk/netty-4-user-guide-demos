package com.waylau.java.demo.reactor.basic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Reactor
 * 
 * @since 1.0.0 2019年11月14日
 * @author <a href="https://waylau.com">Way Lau</a>
 */
public class Reactor implements Runnable {

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    public Reactor(int port) throws IOException { // Reactor初始化
        selector = Selector.open(); // 打开一个Selector
        serverSocketChannel = ServerSocketChannel.open(); // 建立一个Server端通道
        serverSocketChannel.socket().bind(new InetSocketAddress(port)); // 绑定服务端口
        serverSocketChannel.configureBlocking(false); // selector模式下，所有通道必须是非阻塞的
        
        // Reactor是入口，最初给一个channel注册上去的事件都是accept
        SelectionKey sk = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        // 附加回调对象Acceptor
        sk.attach(new Acceptor(serverSocketChannel, selector));
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("主线程");
            while (!Thread.interrupted()) {
            	// 就绪事件到达之前，阻塞
                selector.select();
                String preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
                try {
                    System.out.println(preFix + " select 阻塞1s");
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // 拿到本次select获取的就绪事件
                Set<SelectionKey> selected = selector.selectedKeys(); 
                Iterator<SelectionKey> it = selected.iterator();
                while (it.hasNext()) {
                    // 任务分发
                    SelectionKey next = it.next();
                    preFix = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "["+Thread.currentThread().getName()+"]";
                    System.out.println(preFix+ "[Server]获取到事件: " + next.interestOps());
                    dispatch(next);
                }
                selected.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void dispatch(SelectionKey k) {
    	// 附带对象为Acceptor
        Runnable r = (Runnable) (k.attachment());

        if (r != null) {
            long s = System.currentTimeMillis();
            r.run();
            long e = System.currentTimeMillis() - s;
//            System.out.println("---------cost time: " + e);
        }
    }
}
