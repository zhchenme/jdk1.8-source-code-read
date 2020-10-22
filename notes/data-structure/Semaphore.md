### 一个例子

```java
public class SemaphoreSamples {
    static Semaphore semaphore = new Semaphore(3, true);
    static final int N = 5;

    public static void main(String[] args) {
        for (int i = 0; i < N; i++) {
            new Worker(i, semaphore).start();
        }
    }

    static class Worker extends Thread {
        private final int num;
        private final Semaphore semaphore;

        public Worker(int num, Semaphore semaphore) {
            this.num = num;
            this.semaphore = semaphore;
        }

        @Override
        public void run() {
            try {
                semaphore.acquire();
                System.out.println("工人" + this.num + "占用一个机器在生产...");
                Thread.sleep(1000);
                System.out.println("工人" + this.num + "释放出机器");
                semaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
```

🌰来源：[Java并发编程：CountDownLatch、CyclicBarrier和Semaphore](https://www.cnblogs.com/dolphin0520/p/3920397.html)

输出：

![](https://raw.githubusercontent.com/zhchenme/jdk1.8-source-code-read/master/img/SemamphoreSamples.png)

当 `Semaphore` 的条件满足时，后面的线程会被阻塞，直到有其他线程释放了资源其他线程才可以执行。

### 源码分析

构造函数：

```java
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
```

公平模式下尝试获取锁：

```java
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }


    final int nonfairTryAcquireShared(int acquires) {
        for (;;) {
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                compareAndSetState(available, remaining))
                return remaining;
        }
    }
```

`remaining < 0` 时表示尝试获取锁失败，失败后加入等待队列，通过 AQS 实现。

释放锁：

```java
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    protected final boolean tryReleaseShared(int releases) {
        for (;;) {
            int current = getState();
            int next = current + releases;
            if (next < current) // overflow
                throw new Error("Maximum permit count exceeded");
            if (compareAndSetState(current, next))
                return true;
        }
    }
```

释放锁会增加 AQS 状态值，只要状态值大于 0 就不会有线程阻塞。

知道是那么回事，底层都围绕 AQS 实现，懂了 AQS 才能融会贯通，这些都是小菜。
