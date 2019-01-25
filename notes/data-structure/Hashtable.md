### 一、Hashtable 概述

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190124150135760.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NvZGVqYXM=,size_16,color_FFFFFF,t_70)

`Hashtable` 底层基于数组与链表实现，通过 `synchronized` 关键字保证在多线程的环境下仍然可以正常使用。虽然在多线程环境下有了更好的替代者 `ConcurrentHashMap`，但是作为一个面试中高频的知识点，我们还是有必要了解一下其内部实现细节的。

**1.1 内部属性**

``` java
    // 内部数组
    private transient Entry<?,?>[] table;
    // 键值对数量
    private transient int count;
    // 扩容阈值
    private int threshold;
    // 加载因子
    private float loadFactor;
```

内部属性与 `HashMap` 几乎一致，`HashMap` 中的 `threshold` 属性并不止是扩容阈值，还有另一个作用：哈希表容量大小，这一点要注意区分。

**1.2 相关构造函数**

``` java
    public Hashtable(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load: "+loadFactor);

        if (initialCapacity==0)
            initialCapacity = 1;
        this.loadFactor = loadFactor;
        // 初始化哈希表数组大小，HashMap 中初始化容量大小必须是 2 的幂，但是 Hashtable 没有这个限制
        table = new Entry<?,?>[initialCapacity];
        // 初始化扩容阈值
        threshold = (int)Math.min(initialCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
    }
```

默认初始化大小与加载因子的构造函数，`HashMap` 的默认初始化大小是 16，而 `Hashtable` 是 11，加载因子默认都是 0.78.

``` java
    public Hashtable() {
        this(11, 0.75f);
    }	
```

**1.3 Entry**

``` java
    private static class Entry<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Entry<K,V> next;

        protected Entry(int hash, K key, V value, Entry<K,V> next) {
            this.hash = hash;
            this.key =  key;
            this.value = value;
            this.next = next;
        }
        ......
    }
```

没有什么好说的，唯一注意区分的是，在 `HashMap` 中对应的是 `Node` 结构。

### 二、源码分析

**2.1 put 方法**

``` java
    public synchronized V put(K key, V value) {
        // Make sure the value is not null
        // key（如果为 null，计算哈希值时会抛异常），value 不允许为 null
        if (value == null) {
            throw new NullPointerException();
        }

        // Makes sure the key is not already in the hashtable.
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        // 计算对应的桶位置
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> entry = (Entry<K,V>)tab[index];
        // 如果桶位置上对应的链表不为 null，则遍历该链表
        for(; entry != null ; entry = entry.next) {
            // key 重复，value 替换，返回老的 value
            if ((entry.hash == hash) && entry.key.equals(key)) {
                V old = entry.value;
                entry.value = value;
                return old;
            }
        }

        // 添加新的键值对
        addEntry(hash, key, value, index);
        // 添加成功返回 null
        return null;
    }
```

`put` 方法中并没有直接添加键值对，而是通过 `addEntry` 方法来完成添加的过程。

``` java
    private void addEntry(int hash, K key, V value, int index) {
        modCount++;

        Entry<?,?> tab[] = table;
        // 延迟 rehash？先判断是否需要扩容再 count++
        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = table;
            hash = key.hashCode();
            // 扩容后，新的键值对对应的桶位置可能会发生变化，因此要重新计算桶位置
            index = (hash & 0x7FFFFFFF) % tab.length;
        }

        // Creates the new entry.
        @SuppressWarnings("unchecked")
                // 获取桶位置上的链表
        Entry<K,V> e = (Entry<K,V>) tab[index];
        // 头插法插入键值对
        tab[index] = new Entry<>(hash, key, value, e);
        // count++
        count++;
    }
```

添加的键值对的方法还是比较简单的，先根据哈希值计算出在哈希表数组中对应的桶位置，遍历当前桶位置上的链表（如果存在），判断 key 是否重复，如果重复用新的 value 覆盖旧的 value，返回 旧的 value。如果 key 不重复，则调用添加键值对的方法（`addEntry`），`addEntry` 首先判断了是否需要扩容，如果需要扩容则先进行哈希表的扩容，并重新计算添加的键值对的桶位置，如果不需要扩容，则直接在头节点插入新的键值对。

⚠️`HashMap` 先添加键值对，然后判断是否需要扩容，`Hashtable` 是先判断是否需要扩容然后再添加键值对，这一点需要区分下。

还有一点需要注意的是:java8 中 `HashMap` 通过尾插法插入新的键值对，`Hashtable` 通过尾插法插入键值对。


**2.2 rehash 方法**

上面我们提到了扩容机制，下面我们就来看一下其具体实现。

``` java
    protected void rehash() {
        // 获取老哈希表容量
        int oldCapacity = table.length;
        Entry<?,?>[] oldMap = table;

        // overflow-conscious code
        // 新哈希表容量为原容量的 2 倍 + 1，与 HashMap 不同
        int newCapacity = (oldCapacity << 1) + 1;
        // 容量很大特殊处理
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            if (oldCapacity == MAX_ARRAY_SIZE)
                // Keep running with MAX_ARRAY_SIZE buckets
                return;
            newCapacity = MAX_ARRAY_SIZE;
        }
        // 初始化新哈希表数组
        Entry<?,?>[] newMap = new Entry<?,?>[newCapacity];

        modCount++;
        // 重置扩容阈值，与 HashMap 不同，HashMap 直接把 threshold 也扩大为原来的两倍
        threshold = (int)Math.min(newCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
        // 重置哈希表数组
        table = newMap;

        // 从底向上进行 rehash
        for (int i = oldCapacity ; i-- > 0 ;) {
            // 获取旧哈希表对应桶位置上的链表
            for (Entry<K,V> old = (Entry<K,V>)oldMap[i] ; old != null ; ) {
                // 链表
                Entry<K,V> e = old;
                // 重置继续遍历
                old = old.next;

                // 获取在新哈希表中的桶位置，键值对逐个进行 rehash
                // HashMap 会构造一个新的链表然后整个链表进行 rehash
                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                // 头插法 rehash
                e.next = (Entry<K,V>)newMap[index];
                // 自己做头节点
                newMap[index] = e;
            }
        }
    }
```

与 `HashMap` 相对比，`Hashtable` 的 rehash 过程也比较简单，首先将容量扩大为原来的 2 倍 + 1，接着重置 `threshold`，赋值新的哈希表数组之后就进行键值对 rehash 了。键值对 rehash 的时候从老哈希表数组尾部开始，获取对应桶位置上的链表，键值对逐个进行 rehash。

**2.3 get 方法**

相对于上面两个方法，`get` 方法相对来说又简单了很多。

``` java
    public synchronized V get(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        // 计算 key 对应的桶位置
        int index = (hash & 0x7FFFFFFF) % tab.length;
        // 遍历桶位置上的链表（如果存在）
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
        	// 找到对应的 key，直接返回对应的 value
            if ((e.hash == hash) && e.key.equals(key)) {
                return (V)e.value;
            }
        }
        return null;
    }
```

### 三、other

在分析源码的时候对比了很多与 `HashMap` 的不同点，在看 `Hashtable` 源码的时候建议与 `HashMap` 对比着看，可以加深对 map 的理解。

`Hashtable` 的内部实现相对来说比较简单，数据结构通过数组和链表实现。如果你需要在并发编程中使用 map，建议使用 `ConcurrentHashMap`，性能相对于 `Hashtable` 要高很多，后面有机会我们再讨论 `ConcurrentHashMap` 的内部实现。