### 1 类继承关系

``` java
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {}
```

说明：`LinkedBlockingQueue` 继承了 `AbstractQueue` 抽象类，`AbstractQueue` 定义了对队列的基本操作；同时实现了 `BlockingQueue` 接口，`BlockingQueue` 表示阻塞型的队列，其对队列的操作可能会抛出异常；同时也实现了 `Searializable` 接口，表示可以被序列化。

### 2 Node 结构

``` java
static class Node<E> {
    // 元素
    E item;
    // next域
    Node<E> next;
    // 构造函数
        Node(E x) { item = x; }
    }
```

说明：`Node` 类非常简单，包含了两个域，分别用于存放元素和指示下一个结点。

### 3 类属性

``` java
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    // 版本序列号
    private static final long serialVersionUID = -6903933977591709194L;
    // 容量
    private final int capacity;
    // 元素的个数
    private final AtomicInteger count = new AtomicInteger();
    // 头结点
    transient Node<E> head;
    // 尾结点
    private transient Node<E> last;
    // 取元素锁
    private final ReentrantLock takeLock = new ReentrantLock();
    // 非空条件
    private final Condition notEmpty = takeLock.newCondition();
    // 存元素锁
    private final ReentrantLock putLock = new ReentrantLock();
    // 非满条件
    private final Condition notFull = putLock.newCondition();
}
```

可以看到 `LinkedBlockingQueue` 包含了读、写重入锁（与 `ArrayBlockingQueue` 不同，`ArrayBlockingQueue` 只包含了一把重入锁），读写操作进行了分离，并且不同的锁有不同的 `Condition` 条件（与 `ArrayBlockingQueue` 不同，`ArrayBlockingQueue` 是一把重入锁的两个条件）。

### 4 构造函数

无参构造函数：容量默认为Integer的最大值

``` java
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }
```

单参构造函数

``` java
public LinkedBlockingQueue(int capacity) {
        // 初始化容量必须大于0
        if (capacity <= 0) throw new IllegalArgumentException();
        // 初始化容量
        this.capacity = capacity;
        // 初始化头结点和尾结点
        last = head = new Node<E>(null);
    }
```

集合参构造函数：此构造函数注意点,集合里不能有null元素，否则会抛异常

``` java
public LinkedBlockingQueue(Collection<? extends E> c) {
        // 调用重载构造函数
        this(Integer.MAX_VALUE);
        // 存锁
        final ReentrantLock putLock = this.putLock;
        // 获取锁
        putLock.lock(); // Never contended, but necessary for visibility
        try {
            int n = 0;
            for (E e : c) { // 遍历c集合
                if (e == null) // 元素为null,抛出异常
                    throw new NullPointerException();
                if (n == capacity) // 
                    throw new IllegalStateException("Queue full");
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }
```

### 5 核心函数分析

`put` 函数

``` java
    public void put(E e) throws InterruptedException {
        // 元素为 null 直接抛出异常
        if (e == null) throw new NullPointerException();
        int c = -1;
        Node<E> node = new Node<E>(e);
        // 获取添加元素的锁
        final ReentrantLock putLock = this.putLock;
        // 获取当前队列中的元素数量
        final AtomicInteger count = this.count;
        // 加锁
        putLock.lockInterruptibly();
        try {
            // 当队列已满时，notFull 阻塞
            while (count.get() == capacity) {
                notFull.await();
            }
            // 加入新元素
            enqueue(node);
            // 元素数量 + 1
            c = count.getAndIncrement();
            // 当元素不满时唤醒 notFull
            // Q：为什么不是在移除元素时唤醒呢？这一步有什么作用？
            // A：c + 1 表示阻塞队列中现在总共的元素数
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        // 没有元素时唤醒 notEmpty，注意释放锁的时机
        // 注意 c 的初始值为 -1，判断为 0 时表示阻塞队列中有一个元素
        if (c == 0)
            signalNotEmpty();
    }
```


  
`offer` 函数

``` java
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        // 队列已满直接返回 false
        if (count.get() == capacity)
            return false;
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            // 队列不满时插入元素，并唤醒 notFull
            if (count.get() < capacity) {
                enqueue(node);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return c >= 0;
    }
```

`take` 函数

``` java
    public E take() throws InterruptedException {
        E x;
        // 注意 c 的值是 -1
        int c = -1;
        // 获取元素数量，和 takeLock
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        // 出队加锁
        takeLock.lockInterruptibly();
        try {
            // 当队列中没有元素时 notEmpty 锁阻塞
            while (count.get() == 0) {
                notEmpty.await();
            }
            // 出队
            x = dequeue();
            c = count.getAndDecrement();
            // 出队后，队列中还有元素唤醒 notEmpty
            // Q：为什么这里判断的是大于 1 而不是 0？
            // A：getAndDecrement 方法先获取值再减一，因此当 c = 1 时即表示阻塞队列中没有任何元素
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        // c == capacity 表示原本阻塞队列已满，但是出队了一个元素，此时阻塞队列并不是满的状态
        if (c == capacity)
            signalNotFull();
        return x;
    }
```