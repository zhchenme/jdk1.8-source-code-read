**前置知识**

* ReentrantLock 可重入锁
* PriorityQueue 优先级无界阻塞队列（二叉堆原理）

### 一、概述

DelayQueue 是一个支持延时获取元素的无界阻塞队列（内部是使用 PriorityQueue 来做队列进行入队和出队）。里面的元素都是 “可延期” 的元素，队列头部元素是最先 “到期”的元素，这是基于排序规则 Comparable 和 PriorityQueue 实现的。当队列里面没有元素到期，是不能从队列头部获取元素的，即使有元素的情况下也是不行的，只有在元素到期后才能获取到。

**1.1 DelayQueue 内部结构**

``` java
// Delayed 接口是实现延时操作的关键
public class DelayQueue<E extends Delayed> extends AbstractQueue<E>
    implements BlockingQueue<E> {
    /**
     * 可重入锁
     */
    private final transient ReentrantLock lock = new ReentrantLock();
    /**
     * 优先级队列 入队和出队的真正队列
     */
    private final PriorityQueue<E> q = new PriorityQueue<E>();

    /**
     * 等待队列头部元素的线程，用于优化阻塞
     * leader 就是一个信号，告诉其它线程不要再去获取元素了，它们延迟时间还没到期，只有 leader 对应的线程取到数据别的线程才能取数据
     */
    private Thread leader = null;
    
    /**
     * 锁的条件，可以有选择的唤醒线程
     */
    private final Condition available = lock.newCondition();
   /**
    *  代码省略
    */
}
```

**1.2 Delayed 接口**

Delayed 接口用来标记在给定延迟时间之后执行的对象，它定义了 getDelay 方法用来获取与当前对象相关的剩余过期时间。同时该接口继承了 Comparable 接口，因此它也具备排序功能。至此，对于需要延迟操作的对象可以通过这个两个方法来确定过期的先后顺序。

``` java
public interface Delayed extends Comparable<Delayed> {

    // 获取对象剩余过期时间
    long getDelay(TimeUnit unit);
}

```

**1.3 具体使用**

需要延时操作的对象实现 Delayed 接口，重写 getDelay 和 compareTo 方法即可




### 二、相关方法

**2.1 构造方法**

``` java
  // 默认构造方法
  public DelayQueue() {}
  // 通过集合初始化
  public DelayQueue(Collection<? extends E> c) {this.addAll(c); }
```

说明：DelayQueue 内部组合 PriorityQueue，对元素的操作都是通过 PriorityQueue 来实现的

**2.2 offer 方法**

将指定的元素插入到此队列中，永远会返回 true

``` java
 /**
     * add 和 put 方法也是调用这个方法
     * @param e
     * @return
     */
    public boolean offer(E e) {
        // 添加元素的时候加锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 使用优先级队列加入数据
            q.offer(e);
            // 如果优先级队列头部元素正是刚才拿到锁的线程执行的入参数据，那么等待队列头部元素的线程就置空，并且唤醒该锁阻塞的线程
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }
            // 添加成功，因为是无界队列，这里永远返回 true
            return true;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
```

注意：

* 在判断当前加入队列的元素是否为首元素时，是首元素时设置 leader=null，这就是优化阻塞的操作。peek 并不一定是当前添加的元素，如果此时队列头是当前添加元素，说明当前元素的优先级最小也就即将过期的，这时候唤醒等待的线程，通知他们队列里面有元素了
* 指定等待时间的 offer 方法没有意义，因为队列是无界的，不会进行等待



**2.3 poll 方法**

获取并移出此队列头不元素，如果队列为空就返回 null

``` java
public E poll() {
        final ReentrantLock lock = this.lock;
        //获取同步锁
        lock.lock();
        try {
            //获取队列头
            E first = q.peek();
            //如果队列头为 null 或者 延时还没有到，则返回 null。 getDelay 就是 Delayed 中方法
            if (first == null || first.getDelay(NANOSECONDS) > 0)
                return null;
            else
                return q.poll(); //元素出队
        } finally {
            lock.unlock();
        }
    }
```



### poll 方法（带有等待时间）

获取并移除此队列的头部元素，在指定的超时时间前等待，超时时间> 延迟时间肯定可以获取到元素，设置 leader 为当前线程，等待延迟时间到期

``` java
public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        //超时等待时间
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        //可中断的获取锁
        lock.lockInterruptibly();
        try {
            for (;;) {
                //获取队列头元素
                E first = q.peek();
                //元素为空，也就是队列为空
                if (first == null) {
                    // 达到超时指定时间，返回 null 
                    if (nanos <= 0)
                        return null;
                    else
                        // 如果还没有超时，那么进行超时等待
                        nanos = available.awaitNanos(nanos);
                } else {
                    //获取元素延迟时间
                    long delay = first.getDelay(NANOSECONDS);
                    //延时到期
                    if (delay <= 0)
                        //返回出队元素
                        return q.poll(); 
                    //延时未到期，超时到期，返回 null
                    if (nanos <= 0)
                        return null;
                    // 置空的必要性
                    first = null; 
                    // 超时等待时间 < 延迟时间 或者有其它线程在等待数据
                    if (nanos < delay || leader != null)
                        //阻塞等待 nanos 时间
                        nanos = available.awaitNanos(nanos);
                    else {
                        //超时等待时间 > 延迟时间 并且没有其它线程在等待，那么当前元素成为 leader
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                        //等待  延迟时间 超时
                            long timeLeft = available.awaitNanos(delay);
                            //还需要继续等待 nanos
                            nanos -= delay - timeLeft;
                        } finally {
                            //清除 leader
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            //唤醒阻塞在 available 的一个线程，表示可以取数据了
            if (leader == null && q.peek() != null)
                available.signal();
            //释放锁
            lock.unlock();
        }
    }

```

小结：

* 如果队列为空，如果超时时间未到，则进行等待，否则返回 null 
* 队列不空，取出队头元素，如果延迟时间到，则返回元素，否则 如果超时 时间到 返回 null 
* 超时时间未到，并且超时时间 < 延迟时间或者有线程正在获取元素，那么进行等待 
* 超时时间> 延迟时间，那么肯定可以取到元素，设置 leader 为当前线程，等待延迟时间到期



**2.4 take 方法**

获取并移除此队列的头部，在元素变得可用之前一直等待

``` java
 public E take() throws InterruptedException {
        // 获取可重入锁
        final ReentrantLock lock = this.lock;
        // 可中断的获取锁
        lock.lockInterruptibly();
        try {
            for (;;) {
                // 获取首部元素
                E first = q.peek();
                // 首部元素为空就阻塞，等待被唤醒
                if (first == null)
                    available.await();
                else {
                    // 首部不为空就是判断它的超时时间
                    long delay = first.getDelay(NANOSECONDS);
                    // <=0 表示已经过期，可以出队
                    if (delay <= 0)
                        return q.poll();
                    // 首部有元素但是还没超时，置空
                    first = null; 
                    // leader 不为空就说明，当前有等待队列头部元素的线程，那么当前线程就进行阻塞
                    if (leader != null)
                        available.await();
                    else {
                        // 如果没有线程在等待队列头部元素，就将 leader 设置为当前线程
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            // 进入超时阻塞状态
                            available.awaitNanos(delay);
                        } finally {
                            // 置空 leader
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            // 唤醒阻塞线程
            if (leader == null && q.peek() != null)
                available.signal();
            lock.unlock();
        }
    }
```

注意

设置 first = null 是必要的，设置为 null 的主要目的是为了避免内存泄漏。场景：线程 A 到达，队列首部元素没有到期，设置 leader = 线程 A。这时线程 B 进来了，因为 leader != null，则会阻塞，线程 C 一样。当线程阻塞完毕了，获取队列首部元素成功，这个时候对列首部元素本应该被回收掉，但是它还被线程 B、线程 C 持有着，是强引用状态，JVM 无法回收。如果线程过多，那么就会无限期的不能回收，就会造成内存泄漏。


### 三、应用场景

* 缓存系统的设计：使用 DelayQueue 保存缓存元素的有效期，使用一个线程循环查询 DelayQueue，一旦能从 DelayQueue 中获取元素时，就表示有缓存到期了。 
* 定时任务调度：使用 DelayQueue 保存当天要执行的任务和执行时间，一旦从 DelayQueue 中获取到任务就开始执行，比如 Timer 就是使用 DelayQueue 实现的。

*使用 DelayQueue 延迟队列处理超时订单案例：* https://my.oschina.net/u/3081871/blog/1790780/



### 四、总结

* DelayQueue 内部通过组合 PriorityQueue 来实现存储和维护元素顺序的。

* DelayQueue 存储元素必须实现 Delayed 接口，通过实现 Delayed 接口，可以获取到元素延迟时间，以及可以比较元素大小（Delayed 继承 Comparable）

* DelayQueue 通过一个可重入锁来控制元素的入队出队的行为

* DelayQueue 中 leader 标识 用于减少线程的竞争，表示当前有其它线程正在获取队头元素。

* PriorityQueue 只是负责存储数据以及维护元素的顺序，具体的延迟取数据则是在 DelayQueue 内部进行判断控制的







 



