### 一、CountDownLatch 概述

**1.1 什么是 CountDLatch**

闭锁（`CountDownLatch`）是 `java.util.concurrent` 包下的一种同步工具类。闭锁可以用来确保某些活动直到其他活动都完成后才执行。

闭锁相当于一扇门：在闭锁到达结束状态之前，这扇门一直是关闭的，并且没有任何线程能通过，当达到结束状态时，这扇门会打开，并允许所有的线程通过。

**1.2 CountDownLatch 的应用场景**

- 确保某个计算在其需要的所有资源都被初始化之后才执行
- 确保某个服务在其依赖的所有其他服务都已经启动之后才启动
- 等待直到每个操作的所有参与者都就绪再执行（比如打麻将时需要等待四个玩家就绪）

**1.3 CountDownLatch 简单应用**

我们知道 4 个人玩纸牌游戏一定会先等所有玩家就绪后才会发牌，下面我们就来用闭锁简单的模拟一下。

``` java
public class CountDownLatchTest {
    /**
     * 初始化需要等待的 4 个事件
     */
    private static CountDownLatch latch = new CountDownLatch(4);

    public static void main(String[] args) throws InterruptedException {
        // 创建 4 个线程分别代表 4 个玩家
        new Thread(() -> { System.out.println("玩家 1 已就绪"); latch.countDown(); }).start();
        new Thread(() -> { System.out.println("玩家 2 已就绪"); latch.countDown(); }).start();
        new Thread(() -> { System.out.println("玩家 3 已就绪"); latch.countDown(); }).start();
        new Thread(() -> { System.out.println("玩家 4 已就绪"); latch.countDown(); }).start();
        
        // 所有玩家就绪前一直阻塞
        latch.await();
        System.out.println("所有玩家已就绪，请发牌");
    }
}
```

下面是控制台输出：

![在这里插入图片描述](https://img-blog.csdnimg.cn/2019031118084154.jpg)

### 二、CountDownLatch 原理分析

`CountDownLatch` 底层是基于 AQS 实现的，如果不懂 AQS 原理的小伙伴需要先了解下 AQS 再来看这篇文章。

**2.1 API 相关方法**

构造函数：

``` java
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        // 初始化 count 值
        this.sync = new Sync(count);
    }
```

`CountDownLatch` 内部有一个 `Sync` 同步对象，这个对象是一个内部类实现了 AQS，下面我们会具体来看方法实现。

`await` 方法：

``` java
    public void await() throws InterruptedException {
        // 共享式检查是否中断，如果中断抛出异常
        // 调用 tryAcquireShared 方法尝试获取同步状态，当闭锁内的线程执行完毕后尝试获取成功，直接返回
        sync.acquireSharedInterruptibly(1);
    }
```

`countDown` 方法：

``` java
    public void countDown() {
    	// 调用 releaseShared 每次使同步状态值减 1
        sync.releaseShared(1);
    }
```

通过上面的 API 我们应该能知道其大概的原理了，在 `CountDownLatch` 初始化的时候会有一个初始的同步状态值，这个同步状态值可以理解为放行前的所要执行的线程数，每次调用 `countDown` 方法时就把同步状态值减 1，`await` 方法会自旋检查同步状态值是否为 0，当不为 0 时会阻塞线程，当为 0 时会直接返回，该方法是支持相应 中断的，当线程中断时会抛出异常。因此该方法可以理解为一扇门，只有当指定数量的线程执行完后，才会执行后续的代码。

上面我们已经理解了大概的流程，下面来看下具体的实现代码。

**2.2 Sync 同步类**

``` java
    private static final class Sync extends AbstractQueuedSynchronizer {
        // 初始化闭锁 count 值
        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        //  通过共享方式尝试获取锁
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
		// 通过共享方式尝试释放锁
		// 因为该方法是线程共享的，因此需要通过 CAS 操作保证线程安全
        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                // 同步状态值在上一次置 0 时已经放行，因此返回 false
                if (c == 0)
                    return false;
                // 同步状态值 - 1
                int nextc = c-1;
                // 为 0 时返回 true
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }
```

内部代码很简单，如果你明白了 AQS 的内部原理，这些代码是很容易理解的，如果你对这里的代码感觉到陌生，那么你一定要好好的再去了解下 AQS 了。

AQS 的原理设计的很巧妙，相对来说也比较难理解，后面想梳理这块内容的时候会尝试着总结一下，如果你对这块感兴趣，也可以到对应的源码目录下去看源码。


### 参考资料

《Java 并发编程实战》