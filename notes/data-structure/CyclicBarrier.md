### 一个例子

```java
public class CyclicBarrierSamples {

    private static final ExecutorService SERVICE = Executors.newFixedThreadPool(4);

    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(4, () -> System.out.println("4人已到齐，请系好安全带，现在出发赶往目的地!"));

    public static void main(String[] args) {

        IntStream.range(0, 8).forEach(i -> SERVICE.submit(() -> {
            try {
                System.out.println("到达指定拼车地点 !");
                CYCLIC_BARRIER.await();
                System.out.println("出发了 !");
            } catch (InterruptedException | BrokenBarrierException exception) {
                exception.printStackTrace();
            }
        }));
        SERVICE.shutdown();
    }
}
```

测试结果如图：

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/CylicBarrierSamples.png)

和 `CountDownLatch` 不一样的是，`CyclicBarrier` 是一个可以循环使用的并发工具，使用场景也有些许不同 

 - `CountDownLatch` ：线程 A 在一组线程上等待，当这组线程都执行完毕后，线程 A 才会执行
 - `CyclicBarrier`：一组线程在某个条件上等待，条件达成时所有线程被唤醒，接下来继续执行任务

### 源码分析

构造函数：

```java
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }
```

 - parties：栅栏所允许的所有线程数
 - count：栅栏中剩下的可允许线程数，比如：parties = 10, 此时栅栏内已经拦截了 3 个线程，那 count = 10 - 3 = 7
 - barrierCommand：栅栏线程数满足时触发的任务

`await` 方法：

```java
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }
```

`dowait` 方法：

```java
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();

            // 如果线程中断，唤醒所有在等待点等待执行的线程，抛出中断异常
            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }

            // 栅栏中剩下的需要等待的线程数
            int index = --count;
            // 栅栏已满
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    // 执行栅栏构造函数中初始化创建的任务
                    if (command != null)
                        command.run();
                    ranAction = true;
                    // 重置栅栏（下一代可以继续使用），会唤醒栅栏内所有的线程
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            // 栅栏未满，线程等待
            for (;;) {
                try {
                    // 如果没有设置时间限制，直接等待释放资源，直到被唤醒
                    if (!timed)
                        trip.await();
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    // 如果发生中断异常，且当前栅栏没有被销毁，销毁栅栏
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                // 如果被唤醒，说明肯定进入了下一个周期
                if (g != generation)
                    return index;

                // 设置了时间限制，且时间小于等于 0，直接销毁栅栏
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }
```

逻辑比较简单，主要看处理栅栏已满和栅栏未满两种情况：

 - 栅栏已满：执行构造函数中初始化的任务，唤醒栅栏未满时所有等待的线程，开启下一个周期，重置 `count = parties`
 - 栅栏未满：进入阻塞状态，等待被唤醒

线程阻塞时还可以指定阻塞时间，超时后会继续执行任务。

`breakBarrier` 方法：

```java
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }
```

`nextGeneration` 方法：

```java
    private void nextGeneration() {
        // 唤醒所有的线程
        trip.signalAll();
        // 重置栅栏剩下的可容纳的线程数
        count = parties;
        generation = new Generation();
    }
```

`CyclicBarrier` 通过 `ReentrantLock` 实现，底层是一个简单的等待唤醒机制。