在看这篇总结之前，建议大家先熟悉一下 `PriorityQueue`，这里主要介绍 `PriorityBlockingQueue` 一些特殊的性质，关于优先级队列的知识不作着重介绍，因为过程与 `PriorityQueue` 都是一致的。

关于 `PriorityQueue` 的文章，你可以参考这里->[点击前往～](https://github.com/zchen96/jdk1.8-source-code-read/blob/master/notes/data-structure/PriorityQueue.md)

### PriorityBlockingQueue 相关源码分析

**add 方法**

``` java
    public boolean add(E e) {
        return offer(e);
    }
```

`add` 方法主要调用的是 `offer` 方法，下面我们来看 `offer` 方法。

``` java
    public boolean offer(E e) {
        // 队列所有的元素不允许为 null
        if (e == null)
            throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        // 加锁
        lock.lock();
        int n, cap;
        Object[] array;
        // 判断是否需要扩容
        while ((n = size) >= (cap = (array = queue).length))
            tryGrow(array, cap);
        try {
            Comparator<? super E> cmp = comparator;
            // 如果不自定义比较器，则默认为一个小顶堆，从下往上判断进行调整
            if (cmp == null)
                siftUpComparable(n, e, array);
            else
                siftUpUsingComparator(n, e, array, cmp);
            size = n + 1;
            // 唤醒非空条件对象
            notEmpty.signal();
        } finally {
            // 释放锁
            lock.unlock();
        }
        return true;
    }
```

`offer` 方法整体的流程并不是复杂，首先加锁，然后判断是否需要扩容，接着添加元素，添加元素也分了两种情况，一种是没有自定义比较器，默认是小顶堆，如果初始化了自定义比较器，则按照自定义比较器的逻辑添加元素，因为添加了元素，队列肯定不为空，因此要唤醒 `notEmpty` 条件。

我们以不自定义比较器为例，看一下 `siftUpComparable` 方法是如何调整堆结构的。

``` java
    private static <T> void siftUpComparable(int k, T x, Object[] array) {
        Comparable<? super T> key = (Comparable<? super T>) x;
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = array[parent];
            if (key.compareTo((T) e) >= 0)
                break;
            array[k] = e;
            k = parent;
        }
        array[k] = key;
    }
```

`siftUpComparable` 方法与 `PriorityQueue` 中对应的方法简直是一模一样，放一张图在这，就不具体介绍了。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181220220304825.png)

其中比较有意思的是 `tryGrow` 扩容方法，我们接下来看一下这个方法。

``` java
/**
 * Q：扩容操作为什么要允许多个线程进来呢？
 * A：如果整个扩容过程还加锁的话，其他线程是不能修改队列的，
 * 只能等待扩容完后才能继续执行，并发效率比较低
 */
private void tryGrow(Object[] array, int oldCap) {
        // 释放锁
        lock.unlock(); // must release and then re-acquire main lock
        Object[] newArray = null;
        /**
         * compareAndSwapInt：
         *
         * this：当前对象的引用
         * allocationSpinLockOffset：allocationSpinLock 在内存中的偏移量
         * 0：allocationSpinLock 的预期值
         * 1：更新值
         */
        if (allocationSpinLock == 0 && UNSAFE.compareAndSwapInt(this, allocationSpinLockOffset, 0, 1)) {
            try {
                // 当容量小于 64 时容量为原来的两倍 + 2，如果大于等于 64 时扩容为原来的 1.5 倍
                // 与 PriorityQueue 一致
                int newCap = oldCap + ((oldCap < 64) ?
                        (oldCap + 2) : // grow faster if small
                        (oldCap >> 1));
                if (newCap - MAX_ARRAY_SIZE > 0) {    // possible overflow
                    int minCap = oldCap + 1;
                    if (minCap < 0 || minCap > MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError();
                    newCap = MAX_ARRAY_SIZE;
                }
                // 初始化新的数组
                if (newCap > oldCap && queue == array)
                    newArray = new Object[newCap];
            } finally {
                // allocationSpinLock 置 0
                // 因此后面的线程获取锁也可能会尝试 CAS 成功，然后初始化新数组
                allocationSpinLock = 0;
            }
        }
        /**
         * 如果当前线程尝试 CAS 失败，则尝试让步
         * Q：这里为什么要让步？
         * A：因为自己不是成功初始化新数组的线程，就算获取到了线程也不能正确扩容，
         * 因此让步尽量让成功扩容的线程获取锁
         */
        if (newArray == null) // back off if another thread is allocating
            Thread.yield();
        /**
         * Q：在加锁之前，可能由多个数组尝试 CAS 成功，且成功的初始化了新的数组，
         * 那么是不是后面的新数组会覆盖前面的数组呢？
         * A：当然答案肯定是不会的，那么是如何保证正确性的呢？关键在于 queue == array 判断，
         * 因此只有第一个判断成功的线程能正确扩容，其他非第一个线程再进行判断的时候会返回 false，
         * 自然不会进行数组元素拷贝
         */
        lock.lock();
        if (newArray != null && queue == array) {
            // 重置队列内部数组
            queue = newArray;
            // 元素拷贝，同 PriorityQueue
            System.arraycopy(array, 0, newArray, 0, oldCap);
        }
    }
```

这个方法比较特殊的地方在于先释放了锁，然后通过 CAS 操作判断是否需要初始化新数组，尝试 CAS 失败的线程，会做出一个让步，放弃 CPU 时间片，然后与其他线程一同竞争。这个过程我们可以思考以下几个问题：

- 为什么不直接加锁而是通过 CAS 加判断操作完成扩容步骤
- 为什么尝试 CAS 失败的线程需要让步
- 在多线程情况下可能会有多个线程初始化新数组，那如何保证操作一致性

这些问题在上面的方法里都总结了一些自己的想法，如果大家有不同的见解可以留言交流。

**take 方法**

``` java
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        // 加锁（可响应中断）
        lock.lockInterruptibly();
        E result;
        try {
            // 如果队列为空，take 方法会阻塞出队线程
            while ( (result = dequeue()) == null)
                /**
                 * 如果队列中没有元素，会阻塞后续调用 take 方法出队的线程
                 * 直到队列添加了元素后唤醒 notEmpty，才可以继续执行
                 */
                notEmpty.await();
        } finally {
            // 释放锁
            lock.unlock();
        }
        return result;
    }
```

`take` 方法中调用了 `dequeue` 方法，如下：

``` java
    private E dequeue() {
        int n = size - 1;
        if (n < 0)
            return null;
        else {
            Object[] array = queue;
            // 堆顶的元素
            E result = (E) array[0];
            // 堆最底层的元素（最后一个）
            E x = (E) array[n];
            // 把最后一个元素置 null，因为要把它放到堆顶，向下逐步调整堆结构，与 PriorityQueue 一致
            array[n] = null;
            Comparator<? super E> cmp = comparator;
            if (cmp == null)
                siftDownComparable(0, x, array, n);
            else
                siftDownUsingComparator(0, x, array, n, cmp);
            size = n;
            return result;
        }
    }
```

我们还以不自定义比较器为例，看下 `siftDownComparable` 方法。

``` java
    private static <T> void siftDownComparable(int k, T x, Object[] array,
                                               int n) {
        if (n > 0) {
            Comparable<? super T> key = (Comparable<? super T>)x;
            int half = n >>> 1;           // loop while a non-leaf
            while (k < half) {
                int child = (k << 1) + 1; // assume left child is least
                Object c = array[child];
                int right = child + 1;
                if (right < n &&
                        ((Comparable<? super T>) c).compareTo((T) array[right]) > 0)
                    c = array[child = right];
                if (key.compareTo((T) c) <= 0)
                    break;
                array[k] = c;
                k = child;
            }
            array[k] = key;
        }
    }
```

过程与 `ProrityQueue` 还是一样的，就不分析了，放一张图帮助大家理解吧。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181221103647844.png)
