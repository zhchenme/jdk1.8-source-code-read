### 一、什么是 ArrayBlockingQueue

`ArrayBlockingQueue` 是 GUC(java.util.concurrent) 包下的一个线程安全的阻塞队列，底层使用数组实现。

除了线程安全这个特性，`ArrayBlockingQueue` 还有如下特点：
- 当队列已满时，会阻塞后面添加元素 [put(E e)] 的线程，直到调用了移除元素的方法队列不满的情况下会唤醒前面添加元素的线程
- 当队列已空时，会阻塞后面移除元素 [take()] 的线程，直到调用了添加元素的方法队列不为空的情况下会唤醒前面移除元素的线程
- 新添加的元素并不一定在数组的 0 下标位置，因为其内部维护了一个 `putIndex` 属性
- 数组大小确定，通过构造函数初始化阻塞队列大小，没有扩容机制，因为线程阻塞，不存在数组下标越界异常
- 元素都是紧凑的，比如阻塞队列中有两个元素，那这两个元素在数组中下标之差一定是 1
- 插入的元素不允许为 null，所有的队列都有这个特点
- 先进先出（FIFO (first-in-first-out)）

### 二、相关结构介绍

**2.1 内部属性**

了解了 `ArrayBlockingQueue` 内部的属性，可以帮助我们更好的理解阻塞队列。

``` java
    // 底层存储元素的数组
    final Object[] items;
    // 出队序号，如果有一个元素出队，那么后面的元素不会向前移动，
    // 而是将 takeIndex + 1 表示后面要出队的元素的角标
    int takeIndex;
    // 入队序号，表示后续插入的元素的角标，putIndex 不一定大于 takeIndex
    int putIndex;
    // 元素个数
    int count;
    // 内部锁
    final ReentrantLock lock;
    // notEmpty 条件
    private final Condition notEmpty;
    // notFull 条件
    private final Condition notFull;

```

**2.2 构造函数**

`ArrayBlockingQueue` 中共有 3 个构造函数，这里只看其中一个，拿出来简单地分析一下。

``` java
    public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        // 初始化底层数组
        this.items = new Object[capacity];
        // 默认为非公平锁
        lock = new ReentrantLock(fair);
        // 初始化条件对象
        notEmpty = lock.newCondition();
        notFull =  lock.newCondition();
    }
```


**2.3 相关方法特性**

| method name | usage |
| :------: | :------: |
| offer(E e) | 在队列尾部插入元素，如果队列已满，则返回 false |
| put(E e) | 在队列尾部插入元素，如果队列已满，则线程阻塞等待空间可用 |
| add(E e) | 底层调用了 offer(E e) 方法 |
| poll() | 出队，如果队列中没有元素则返回 null |
| take() | 出队，如果队列中没有元素，则线程阻塞，等待新元素插入 |
| peek() | 出队，如果队列中没有元素则返回 null |

上面这么多方法中，只有 `put(E e)` 与 `take()` 两个方法才在一定条件下阻塞线程，下面我们来重点看一下这两个方法。

### 三、源码分析

**3.1 put 方法**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190121154429859.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

``` java
    public void put(E e) throws InterruptedException {
        // 插入的元素不允许为 null
        checkNotNull(e);
        // 获取锁
        final ReentrantLock lock = this.lock;
        /**
         * lock：调用后一直阻塞到获得锁
         * tryLock：尝试是否能获得锁 如果不能获得立即返回
         * lockInterruptibly：调用后一直阻塞到获得锁 但是接受中断信号(比如：Thread、sleep)
         */
        lock.lockInterruptibly();
        try {
            // 如果队列数组已满，则 notFull 阻塞，当有元素被移除后才唤醒 notFull
            while (count == items.length)
                notFull.await();
            // 元素入队
            enqueue(e);
        } finally {
            // 添加完元素后释放锁
            lock.unlock();
        }
    }
```

上面的方法中调用了 `enqueue` 方法，这个 `enqueue` 用于在队尾插入元素，下面是具体实现细节。

``` java
    private void enqueue(E x) {
        // assert lock.getHoldCount() == 1;
        // assert items[putIndex] == null;
        final Object[] items = this.items;
        // 添加元素
        items[putIndex] = x; 
        // 如果插入元素的位置是数组尾部则重置 putIndex 为 0
        if (++putIndex == items.length)
            putIndex = 0;
        count++;
        // 队列中一定有元素，因此唤醒 notEmpty
        notEmpty.signal();
    }
```

代码实现还是比较简单的，先加锁，如果队列没有满的情况下直接在 `putIndex` 的位置插入新元素，如果队列已满则阻塞`notFull.await()` 当前获得锁的添加元素的线程，直到有元素从队列中被移除了，会唤醒 `notFull`，添加元素的线程才会被唤醒继续执行。

**3.2 take 方法**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190121154444272.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

``` java
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            // 如果队列中没有元素，则让 notEmpty 阻塞，添加元素后会唤醒 notEmpty
            while (count == 0)
                notEmpty.await();
            return dequeue();
        } finally {
            lock.unlock();
        }
    }
```

出队 `dequeue` 具体实现细节。

``` java
    private E dequeue() {
        // assert lock.getHoldCount() == 1;
        // assert items[takeIndex] != null;
        final Object[] items = this.items;
        @SuppressWarnings("unchecked")
        E x = (E) items[takeIndex];
        // 元素置 null
        items[takeIndex] = null;
        // 如果出队的是数组中的最后一个元素，则重置 takeIndex 为 0
        if (++takeIndex == items.length)
            takeIndex = 0;
        count--;
        if (itrs != null)
            itrs.elementDequeued();
        // 唤醒 notFull
        notFull.signal();
      
```

出队与入队的原理都是类似的，同样是先加锁，如果队列中没有任何元素，则获得锁的出队的线程阻塞 `notEmpty.await()`，直到有元素被添加到队列中，会唤醒 `notEmpty`，移除元素的线程才会被唤醒继续执行，如果队列中有元素，则直接把 `takeIndex` 位置上的元素出队。

**3.3 other**

上面我们只简单的分析了 4 个方法，但是上面 4 个方法足以让我们了解 `ArrayBlockingQueue` 的实现原理了。其中比较复杂的方法可能就是 `remove` 方法了，因为移除的元素可能在任意一个位置，为了使元素紧凑，会将后面的元素向前移动一个位置，然后重置 `putIndex`，大概的流程就是这样。





