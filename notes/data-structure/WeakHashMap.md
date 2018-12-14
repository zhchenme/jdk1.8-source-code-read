`WeakHashMap` 是一种特殊的 `Map`，它底层的 `Entry key` 是基于弱引用（Weak Reference）实现的，那什么是弱引用呢？下面简单的了解一下 Java 中的四种引用类型。

在 jdk1.2 之后 Java 扩充了四种引用方式，强引用(Strong Reference)、软引用(Soft Reference)、弱引用(Weak Reference)、虚引用(Phantom Reference)这四种，用关系依次减弱。

- 强引用是指在代码中普遍存在的，类似 `Object obj = new Object()；` 这类的引用，只要强引用还存在，垃圾回收器永远不会回收掉引用的对象。
- 软引用是用来描述一些还有用但并非是必要的对象。对于软引用着的对象，在系统将要发生内存溢出异常之前，将会把这类对象列进回收范围进行第二次的回收。如果这次回收仍然没有足够的内存，就会抛出内存溢出异常。在 jdk1.2 中提供了 `SoftReference` 类来实现软引用。
- 弱引用也是用来描述非必须对象的，但是它的强度比软引用更弱一些，被弱引用关联的对象只能生存到下一次的垃圾回收之前。当垃圾收集器工作时，无论当前内存是否足够，都会回收掉只被弱引用关联的对象。在 jdk1.2 中提供了 `WeakReference` 类来实现弱引用。
- 虚引用也被称为幽灵引用或幻影引用，它是最弱的一种引用关系。一个对象是否有虚引用的存在，完全不会对其生存时间造成影响，也无法通过虚引用来取得一个对象的实例。为一个对象设置虚引用关联的唯一目的就是能在这个对象被收集时收到一个系统通知。在 jdk1.2 中提供了 `PhantomReference` 类来实现虚引用。

### 一、WeakHashMap 介绍

**1.1 底层原理**

`WeakHashMap` 底层的 `Entry` 实现了 `WeakReference` 类，看了上面的介绍我们已经知道弱引用只能存活到下一次垃圾回收之前，而 `WeakHashMap` 中维护了一个 `ReferenceQueue`（弱引用队列），当执行 GC 时，那些被弱引用修饰的 `entry` 就会被加入到弱引用队列中，当你对 `WeakHashMap` 进行一些操作时（`get`、`remove` 等），就会从弱引用队列中获取 `entry` 进行比对，如果该 `entry` 已经在弱引用队列中存在，就会把对应的 `WeakHashMap` 中的 `entry value` 清除。

**1.2 底层数据结构原理**

`WeakHashMap` 的底层是基于数组 + 链表（无红黑树）实现的，并且插入元素的方式也与 `HashMap` 不同，`WeakHashMap` 采取头插法的方式插入键值对。另外一个与 `HashMap` 不同的点是 `WeakHashMap` 在构造函数中就已经对哈希表进行了初始化操作，而 `HashMap` 是在 `resize()` 方法中完成初始化的。

到这里大概的知识点就已经讲完了，下面来看一下 `WeakHashMap` 的源码中都有什么。

### 二、WeakHashMap 相关源码解读

**2.1 重要的初始化函数**

`WeakHashMap` 中的其他构造函数底层都是调用了该构造函数进行初始化的。

``` java
    public WeakHashMap(int initialCapacity, float loadFactor) {
        // 容量判断
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Initial Capacity: "+
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        // 加载因子判断
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load factor: "+
                                               loadFactor);
        int capacity = 1;
        /**
         * 通过循环判断的形式初始化哈希表容量，capacity 为大于 initialCapacity 的最小的 2 的幂
         * 计算初始容量的时候，与 HashMap 是完全不同的，HashMap 是调用 tableSizeFor(int cap) 方法
         */
        while (capacity < initialCapacity)
            capacity <<= 1;
        // 初始化哈希表
        table = newTable(capacity);
        this.loadFactor = loadFactor;
        // 初始化扩容阈值，与 HashMap 也是不同的，注意区分
        threshold = (int)(capacity * loadFactor);
    }
```

**2.2 `put` 方法**

``` java
    public V put(K key, V value) {
        Object k = maskNull(key);
        // 计算 key 的哈希值
        int h = hash(k);
        // 获取哈希表
        Entry<K,V>[] tab = getTable();
        // 当前 key 桶位置
        int i = indexFor(h, tab.length);

        // 遍历桶位置上的链表进行查找
        for (Entry<K,V> e = tab[i]; e != null; e = e.next) {
            /**
             * e.get() 用于返回引用，如果该引用对象已被程序或垃圾收集器清除，此方法返回 null
             * eq(k, e.get()) 用于判断 key 与引用
             */
            // key 存在，value 覆盖
            if (h == e.hash && eq(k, e.get())) {
                V oldValue = e.value;
                if (value != oldValue)
                    e.value = value;
                return oldValue;
            }
        }

        modCount++;
        // key 不存在，获取桶位置上的头节点
        Entry<K,V> e = tab[i];
        // 头插法插入新键值对
        tab[i] = new Entry<>(k, value, queue, h, e);
        // size ++，并判断是否需要扩容，扩容为原哈希表的 2 倍
        if (++size >= threshold)
            resize(tab.length * 2);
        return null;
    }
```

`getTable()` 方法进去看一下。

``` java
    private Entry<K,V>[] getTable() {
        expungeStaleEntries();
        return table;
    }
``` 

接下来就是最重要的一个方法了：`expungeStaleEntries()`

**2.3 expungeStaleEntries() 方法** 

`WeakHashMap` 就是通过该方法删除哈希表中的弱引用键值对的。

``` java
    private void expungeStaleEntries() {
        // 遍历弱引用队列
        for (Object x; (x = queue.poll()) != null; ) {
            // 同步处理
            synchronized (queue) {
                @SuppressWarnings("unchecked")
                    Entry<K,V> e = (Entry<K,V>) x;
                // TODO 计算出桶位置，e 是 entry 为什么计算的不是 key 的哈希值
                // 解释上面的疑问：key 在上一次 GC 时可能已经被回收
                int i = indexFor(e.hash, table.length);

                // 获取哈希表的头节点
                Entry<K,V> prev = table[i];
                Entry<K,V> p = prev;
                // 遍历桶位置上的所有节点
                while (p != null) {
                    Entry<K,V> next = p.next;
                    // 若引用队列中的 entry 在当前链表中存在，则删除
                    if (p == e) {
                        // 如果头节点的 entry 在弱引用队列中，重置头节点为后面的节点
                        if (prev == e)
                            table[i] = next;
                        else
                            prev.next = next;
                        // Must not null out e.next;
                        // stale entries may be in use by a HashIterator
                        // 把 value 置 null，GC 回收，size --，仅 value 被回收
                        e.value = null; // Help GC
                        size--;
                        break;
                    }
                    prev = p;
                    p = next;
                }
            }
        }
    }
```
注意上面的方法其实只对 `entry` 的 value 进行了置 `null` 处理，为什么不对 key 也置 `null` 呢？原因就在于 `WeakHashMap` 的 key 是弱引用，当 GC 发生时，key 会被自动清除。

**2.4 resize(int newCapacity) 方法** 

对比上面的 `put` 方法食用更佳。

``` java
void resize(int newCapacity) {
        // 获取老得哈希表，去除了被 GC 回收的键值对
        Entry<K,V>[] oldTable = getTable();
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        /**
         * 新的哈希表数组
         */
        Entry<K,V>[] newTable = newTable(newCapacity);
        // 新老哈希表转换（rehash）
        transfer(oldTable, newTable);
        // 重置哈希表
        table = newTable;

        /*
         * If ignoring null elements and processing ref queue caused massive
         * shrinkage, then restore old table.  This should be rare, but avoids
         * unbounded expansion of garbage-filled tables.
         *
         * 在 getTable() 时可能会有键值对被回收，这么说不太准确，value 被回收时，可能导致新的哈希表容量很小
         */
        if (size >= threshold / 2) {
            threshold = (int)(newCapacity * loadFactor);
        } else {
            expungeStaleEntries();
            // 把新的哈希表再转回老哈希表
            transfer(newTable, oldTable);
            table = oldTable;
        }
    }

    /** Transfers all entries from src to dest tables */
    private void transfer(Entry<K,V>[] src, Entry<K,V>[] dest) {
        for (int j = 0; j < src.length; ++j) {
            // 获取当前桶位置上的链表（头节点）
            Entry<K,V> e = src[j];
            // 老哈希表桶位置置 null，GC 回收
            src[j] = null;
            while (e != null) {
                Entry<K,V> next = e.next;
                // 获取弱引用 key
                Object key = e.get();
                // 如果 key 为 null，说明已经被 GC 回收，此时需要把对应的 value 也置 null
                if (key == null) {
                    e.next = null;  // Help GC
                    e.value = null; //  "   "
                    size--;
                } else {
                    // 计算哈希值，进行 rehash，为什么根据 entry 计算桶位置？
                    int i = indexFor(e.hash, dest.length);
                    // 头插法
                    e.next = dest[i];
                    // 相当于重置头节点
                    dest[i] = e;
                }
                e = next;
            }
        }
    }
```