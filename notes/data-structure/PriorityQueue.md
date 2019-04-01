
### 一、什么是 PriorityQueue

这篇文章带大家去了解一个 jdk 中不常用的数据结构 `PriorityQueue`（优先队列），虽然在项目里用的不多，但是它本身的设计实现还是很值得大家看一看的。

`PriorityQueue` 底层是一个用数组实现的完全二叉树，但它并不只是一个完全二叉树，在没有自定义比较器（自然排序）的情况下，更严格的来讲它是一个基于数组实现的小顶堆（父节点的元素值小于左右孩子节点的元素值）。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181220220238723.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

### 二、PriorityQueue 简介

**2.1 相关属性**

我们知道在 `HashMap` 中有很多属性，比如默认的初始化大小，加载因子等，在 `PriorityQueue` 中也有很多这种常量。下面我们来认识一下，这有助于我们更好的理解 `PriorityQueue`。

``` java
    // 优先队列的默认初始大小是 11
    private static final int DEFAULT_INITIAL_CAPACITY = 11;
    // 底层是一个 Object 数组
    transient Object[] queue;
    // 优先队列中的元素个数
    private int size = 0;
    // 最大容量限制
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    ...
```

**2.2 相关构造函数**

`PriorityQueue` 有很多构造函数，但是有一个重要的构造函数，这里把它贴出来，为什么说它重要呢，因为大部分构造函数（并不是全部）内部都是通过调用这个构造函数进行初始化的。

``` java
   public PriorityQueue(int initialCapacity,
                         Comparator<? super E> comparator) {
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        // 初始化底层 queue 数组
        this.queue = new Object[initialCapacity];
        // 如果自定义比较器则初始化自定义比较器
        this.comparator = comparator;
    }
```

### 三、扩容机制

`PriorityQueue` 的扩容机制其实很简单，如下：

``` java
    private void grow(int minCapacity) {
        int oldCapacity = queue.length;
        // Double size if small; else grow by 50%
        // 当容量小于 64 时容量为原来的两倍 + 2，如果大于等于 64 时扩容为原来的 1.5 倍
        int newCapacity = oldCapacity + ((oldCapacity < 64) ?
                                         (oldCapacity + 2) :
                                         (oldCapacity >> 1));
        // overflow-conscious code
        // 当元素数量非常多时进行单独处理
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // 元素拷贝
        queue = Arrays.copyOf(queue, newCapacity);
    }
```

### 四、添加元素

上面我们已经说了 `PriorityQueue` 在自然排序下（不自定义比较器）是一个用数组实现的小顶堆，下面我们就来看一下其内部具体是怎么实现的。

添加元素的方法有两个，分别是 `add` 与 `offer`，但是 `add` 内部调用的是 `offer` 方法，这里我们只对 `offer` 方法进行分析。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181220220304825.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

由于新插入元素后，可能会导致小顶堆的结构被破坏，因此需要将新插入的元素（在小顶堆的最低层）向上调整，如果插入的元素比父节点大，那么就把父节点调下来，记录父节点的位置后继续向上调整，直到其比父节点元素值大为止。

``` java
    public boolean offer(E e) {
        // 插入元素为 null，直接抛出异常
        if (e == null)
            throw new NullPointerException();
        modCount++;
        // 记录优先队列中的元素个数（也是新元素的插入位置），判断是否需要扩容
        int i = size;
        // 当数组容量不够时进行扩容
        if (i >= queue.length)
            grow(i + 1);
        // size + 1
        size = i + 1;
        // 第一次插入元素
        if (i == 0)
            queue[0] = e;
        else
            // 调整二叉堆
            siftUp(i, e);
        return true;
    }
```

下面我们来看 `siftUp` 方法：

``` java
    private void siftUp(int k, E x) {
        // 判断是否有自定义的比较器
        if (comparator != null)
            siftUpUsingComparator(k, x);
        else
            siftUpComparable(k, x);
    }
```

上面 `siftUp` 方法中做了一个判断，主要是判断用户有没有自定义比较器，由于这两个方法类似，这里我们只看其中一个没有自定义比较器的 `siftUpComparable` 方法。

``` java
    private void siftUpComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>) x;
        while (k > 0) {
            // 获取父节点位置
            int parent = (k - 1) >>> 1;
            // 获取父节点元素
            Object e = queue[parent];
            // 如果插入的元素大于父节点（构成小顶堆），结束循环
            if (key.compareTo((E) e) >= 0)
                break;
            // 如果插入的元素小于父节点元素，将父节点元素调整下来
            queue[k] = e;
            // 记录父节点位置，继续向上判断调整
            k = parent;
        }
        // 调整后将插入的元素放在对应的位置上
        queue[k] = key;
    }
```

理解了原理，看代码的过程就比较容易了，没有很多的代码，花点时间仔细看一下就能理解了。

### 五、移除元素

移除元素的方法也有两个，分别是 `remove` 与 `poll`，与 `remove` 不同的是 `poll` 每次移除的是堆顶的元素，也就是最小的元素，`remove` 可以移除指定的任意元素，并且这个移除只会移除第一次出现的该元素，如果后面也有该元素是不会移除的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181221103647844.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

`poll` 方法相比较于 `remove` 方法要简单一些，因为 `poll` 每次移除的是堆顶的元素，那么在调整二叉堆的时候只需要从头开始调整就好了(把队尾的元素移到队首)，如果孩子节点比父节点小，就把较小的孩子节点移到父节点的位置，记录移动的孩子的节点的位置，继续向下调整即可，而 `remove` 方法移除的元素可能是介于堆顶与堆尾的元素，这时就不仅需要向下调整了，必要的时候也需要向上进行调整才能维持小顶堆。

这里只贴出 `poll` 的方法，相信你对 `poll` 方法理解了，`remove` 方法耐心看下来肯定也是可以理解的。

``` java
    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0)
            return null;
        // size 减 1
        int s = --size;
        modCount++;
        // 获取队首的元素
        E result = (E) queue[0];
        // 获取队尾的元素（队首元素被移除，把队尾元素放在队首，从上往下调整二叉堆）
        E x = (E) queue[s];
        // 队尾元素置 null
        queue[s] = null;
        if (s != 0)
            // 调整二叉堆
            siftDown(0, x);
        return result;
    }
```

`siftDown` 如下：

``` java
    private void siftDown(int k, E x) {
        // 判断是否自定义了比较器
        if (comparator != null)
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }
```

同 `offer` 方法一样，`poll` 方法在调整小顶堆时也分了自然排序与自定义排序两种情况，这里我们仍然只了解其中一个自然排序的方法 `siftDownComparable`。

``` java
    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>)x;
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            // 获取左孩子节点所在的位置
            int child = (k << 1) + 1; // assume left child is least
            // 获取左孩子节点元素值
            Object c = queue[child];
            // 右孩子节点所在位置
            int right = child + 1;
            if (right < size &&
                ((Comparable<? super E>) c).compareTo((E) queue[right]) > 0)
                // 记录左右孩子中最小的元素
                c = queue[child = right];
            // 如果父节点比两个孩子节点都要小，就结束循环
            if (key.compareTo((E) c) <= 0)
                break;
            // 把小的元素移到父节点的位置
            queue[k] = c;
            // 记录孩子节点所在的位置，继续向下调整
            k = child;
        }
        // 最终把父节点放在对应的位置上，使其保持一个小顶堆
        queue[k] = key;
    }
```

关于 `PriorityQueue` 我们就先了解这么多，如果你有兴趣可以从头到尾把对应的源码看一遍～

PS：因为 `PriorityQueue` 在初始化的时候允许用户传入自定义比较器，因此你也可以自定义比较器使其成为一个大顶堆，就不在这里演示了。

### 参考资料

参考了这篇博客的部分内容，图画的很好，讲解的也很好，感谢此博主大大～🙏[@CarpenterLee](https://www.cnblogs.com/CarpenterLee/p/5488070.html)