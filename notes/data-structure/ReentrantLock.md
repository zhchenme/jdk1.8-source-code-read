### 一、ReentrantLock 概述

**1.1 ReentrantLock 简介**

故名思义，`ReentrantLock` 意为可重入锁，那么什么是可重入锁呢？可重入意为一个持有锁的线程可以对资源重复加锁而不会阻塞。比如下面这样：

``` java
    public synchronized void f1() {
        f2();
    }

    private synchronized void f2() { }
```

`ReentrantLock` 除了支持可重入以外，还支持定义公平与非公平策略，默认情况下采用非公平策略。公平是指等待时间最长的线程会优先获取锁，也就是获取锁是顺序的，可以理解为先到先得。

公平锁保证了锁的获取按照 FIFO（先进先出）原则，但是需要大量的线程切换。非公平锁虽然减少了线程之间的切换增大了其吞吐量，但是可能会造成线程“饥饿”。

**1.2 使用方式**

``` java
    public void f1() {
        ReentrantLock lock = new ReentrantLock();
        try {
            lock.lock();
            // ...
        } finally {
            lock.unlock();
        }
    }
```

与 `synchronized` 不同，使用 `ReentrantLock` 必须显示的加锁与释放锁。

**1.3 与 synchronized 对比**

相同点：

- 可重入，同一线程可以多次获得同一个锁
- 都保证了可见性和互斥性

不同点：

- `ReentrantLock` 可响应中断、可轮回，为处理锁的不可用性提供了更高的灵活性，`synchronized` 不可以响应中断
- `ReentrantLock` 是 API 级别的，`synchronized` 是 JVM 级别（JVM 内置属性）的，因此内置锁可以与特定的栈帧关联起来
- `ReentrantLock` 可以实现公平锁，切可以实现带有时间限制的操作
- `ReentrantLock` 通过 `Condition` 可以绑定多个条件


### 二、源码分析

了解了 `ReentrantLock` 的基本概念后，接下来就一起来看源码吧。其实 `ReentrantLock` 内的源码并不多，原因在于很多源码都在 `AbstractQueuedSynchronizer` 中，因此想要了解 `ReentrantLock` 源码的小伙伴需要先行了解下 AQS。

**2.1 构造函数**


默认构造函数

``` java
    public ReentrantLock() {
        sync = new NonfairSync();
    }
```

有参构造函数

``` java
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
```

上面我们说了 `ReentrantLock` 支持两种同步策略，分别是公平与非公平，从这个构造函数中就能体现出来，相信大家看了这个构造函数也应该想到这两个 `FairSync`，`NonfairSync` 对象应该发挥着至关重要的作用。下面就来分别分析下对应的源码。

**2.2 NonfairSync**

``` java
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        // 加锁
        final void lock() {
            // 无锁重入，通过 CAS 第一次尝试修改同步状态值
            if (compareAndSetState(0, 1))
            	// 设置当前线程独占
                setExclusiveOwnerThread(Thread.currentThread());
            else
                // 锁重入，同步状态值 + 1
                acquire(1);
        }

        // 尝试获取锁，直接调用 Sync 中的 nonfairTryAcquire 方法即可
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }
```

`acquire` 方法是 AQS 中的一个模板方法，这里我们就不多介绍了，还是要提醒下，大家应该先了解 AQS，再来看这篇文章。

`tryAcquire` 调用的是 `nonfairTryAcquire` 方法尝试去获取同步状态，接下来我们看 `Sync` 类中具体做了哪些实现。

**2.3 Sync**

``` java
    abstract static class Sync extends AbstractQueuedSynchronizer {
        abstract void lock();

        // 独占模式下尝试获取锁
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            // 获取同步状态
            int c = getState();
            // 0 表示无状态，独占锁模式下可理解为没有锁重入
            if (c == 0) {
                // 更新同步状态值
                if (compareAndSetState(0, acquires)) {
                    // 独占式设置当前线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 设置独占锁可重入
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                // 重新设置修改后同步的状态值
                setState(nextc);
                return true;
            }
            // 获取失败
            return false;
        }

        // 独占模式下尝试释放锁
        protected final boolean tryRelease(int releases) {
            // 减小同步状态
            int c = getState() - releases;
            // 不是当前线程抛出异常
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            // 如果同步状态只为 0，表示可以释放资源，后面的线程可以尝试去获取锁
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            // 更新减小后的同步状态值
            setState(c);
            return free;
        }
    }
```

上面并没有把 `Sync` 中的所有源码贴出来，这些源码已经足够我们说明问题了。

首先来看 `nonfairTryAcquire` 方法，该方法用于非公平模式下获取同步状态，我们知道 `ReentrantLock` 是支持可重入的锁，因此要考虑两种情况，没有锁重入与有锁重入。其实源码也很简单，有锁重入的情况下只要增加同步状态值即可，这里的同步状态值可以理解为锁重入的次数。等释放锁时将同步状态值再逐次减小，当减小为 0 时表示锁已经释放，这也是 `tryRelease` 方法中做的事情。

`ReentrantLock` 获取锁的方式是独占的，因此释放锁的过程，公平与非公平模式下是相同的。因为考虑到重入的情况，只有当同步状态减小到为 0 时，才返回 `true` 表示释放锁成功。

**2.4 FairSync**


``` java
    static final class FairSync extends Sync {
        final void lock() {
            acquire(1);
        }

        // 尝试获取锁
        protected final boolean tryAcquire(int acquires) {
            // 获取当前线程
            final Thread current = Thread.currentThread();
            // 获取同步状态值
            int c = getState();
            // 在同步状态为 0 的情况下并不是所有线程都可以去获取同步状态，等待时间长的线程会优先获取同步状态
            if (c == 0) {
                // 如果队列中没有等待时间比当前线程时间长的线程，更新同步状态值
                if (!hasQueuedPredecessors() &&
                        compareAndSetState(0, acquires)) {
                    // 设置当前线程独占该锁
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 保证锁可重入
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                // 更新同步状态值
                setState(nextc);
                return true;
            }
            return false;
        }
    }
```

公平策略下获取同步状态的过程与非同步状态是类似的，只不过是多了一个 `hasQueuedPredecessors` 过程判断，这个方法用于判断当前线程对应的节点是否还有前驱节点，如果有则获取失败。下面是具体代码实现：

``` java
    public final boolean hasQueuedPredecessors() {
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        // s 表示头节点的后继节点，如果头节点的后继节点不是当前线程的节点，
        // 表示当前线程并非较先加入队列的线程，因此不能成功获取同步状态
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }
```

非公平策略下释放同步状态与公平策略下相同，上面已经概述过了。

### 参考资料

《JAVA 并发编程的艺术》
[深入理解AbstractQueuedSynchronizer（一）](http://www.ideabuffer.cn/2017/03/15/%E6%B7%B1%E5%85%A5%E7%90%86%E8%A7%A3AbstractQueuedSynchronizer%EF%BC%88%E4%B8%80%EF%BC%89/)
[Java并发之AQS详解](https://www.cnblogs.com/waterystone/p/4920797.html)