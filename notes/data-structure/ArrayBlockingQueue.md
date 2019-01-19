`ArrayBlockingQueue` 内部细节还是很多的，尝试一下，能总结到哪里就是哪里。

### 一、什么是 ArrayBlockingQueue

`ArrayBlockingQueue` 是 GUC(java.util.concurrent) 包下的一个线程安全的阻塞队列，底层使用数组实现。

除了线程安全这个特性，`ArrayBlockingQueue` 还有其他的特点：
- 当队列已满时，会阻塞后面添加元素 [put(E e)] 的线程，直到调用了移除元素的方法队列不满的情况下会唤醒前面添加元素的线程
- 当队列已空时，会阻塞后面移除元素 [take()] 的线程，直到调用了添加元素的方法队列不为空的情况下会唤醒前面移除元素的线程
- 新添加的元素并不一定在数组的 0 下标位置，因为其内部维护了一个 `putIndex` 属性
- 数组大小确定，通过构造函数初始化阻塞队列大小，没有扩容机制，因为线程阻塞，不存在数组下标越界异常
- 元素都是紧凑的，比如阻塞队列中有两个元素，那这两个元素在数组中下标之差一定是 1
- 插入的元素不允许为 null，所有的队列都有这个特点
- 先进先出（FIFO (first-in-first-out)）

### 二、相关结构介绍

**2.1 内部属性**

了解了 `ArrayBlockingQueue` 内部的属性，可以帮助我们更好的理解阻塞队列

``` java
    // 底层存储元素的数组
    final Object[] items;
    // 出队序号，如果有一个元素出队，那么后面的元素不会向前移动，
    // 而是将 takeIndex + 1 表示后面要出队的元素的角标
    int takeIndex;
    // 入队序号，表示后续插入的元素的角标
    int putIndex;
    // 元素个数
    int count;
    // 内部锁
    final ReentrantLock lock;
    //  
    private final Condition notEmpty;
    // 
    private final Condition notFull;

```

**2.2 构造函数**

`ArrayBlockingQueue` 中共有 3 个构造函数，这里只看其中一个，拿出来简单地分析一下

``` java
    public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        // 初始化底层数组
        this.items = new Object[capacity];
        // 默认为非公平锁
        lock = new ReentrantLock(fair);
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

### 三、源码分析
