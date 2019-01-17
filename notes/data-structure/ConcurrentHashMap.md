### 一、start

`ConcurrentHashMap` 在 jdk1.8 中使用 CAS 与 `synchronized` 代替 jdk1.7 中的分段锁，这也能说明
`synchronized` 锁性能在高版本的 jdk 中已经很高了。

jdk1.8 中的 `ConcurrentHashMap` 共有 6000 多行的代码，里面涉及到很多的内部类与属性，如果想要完全
了解 `ConcurrentHashMap` 是一个很耗时间的过程。

`ConcurrentHashMap` 与 `HashMap` 有些数据结构类似，但是其内部细节则有很大的不同。比如 `size()` 方法，
在 `HashMap` 中只需要返回对应的 `size` 属性就可以了，但是在 `ConcurrentHashMap` 中需要一系列的判断才能得到最终的
 `size`。
 
现在只更新一部分源码实现过程，如果后续再回过头来看，会陆续更新。

### 二、put 方法
 
``` java
    public V put(K key, V value) {
        return putVal(key, value, false);
    }
```

``` java
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        // key 与 value 不允许为 null
        if (key == null || value == null) throw new NullPointerException();
        // 哈希值
        int hash = spread(key.hashCode());
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            // 如果哈希表没有初始化，则初始化哈希表数组
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();

            // i 代表插入的桶位置，如果该桶位置上没有任何元素，则直接通过 CAS 插入
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null,
                             new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            // TODO what's this?
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                V oldVal = null;
                // 哈希冲突，加锁，f 为桶位置上的头节点
                synchronized (f) {
                    // 双重校验，一开始查找的位置上头节点没有改变
                    if (tabAt(tab, i) == f) {
                        // 非树节点，非树节点 binCount 代表该链表上的键值对个数
                        if (fh >= 0) {
                            binCount = 1;
                            // 从头节点开始遍历
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                // key 相同，则获取旧的 value，直接结束循环
                                if (e.hash == hash &&
                                    ((ek = e.key) == key ||
                                     (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    // 如果 onlyIfAbsent 为 false，则直接覆盖老得 value
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                // key 不相同，获取后继节点，如果后继节点为 null 表示找到了插入位置
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                                              value, null);
                                    break;
                                }
                            }
                        }
                        // 如果当前桶位置上头节点是树节点，走树节点插入流程
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            // 重置了一次 binCount
                            //  这个 2 有什么特殊意义吗，这个 2 应该是个小于 TREEIFY_THRESHOLD(8) 的任意数，到后面判断后不需要树化，因为本身就是树了
                            binCount = 2;
                            // key 重复，记录旧 value，并根据 onlyIfAbsent 判断是否需要覆盖旧 value
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                if (binCount != 0) {
                    // 判断是否需要转树
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    // 如果 key 重复则返回旧的 value
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        // binCount 一定是大于等于 的数
        addCount(1L, binCount);
        return null;
    }
``` 

`putVal` 方法内部只做了添加键值对的实现，最后调用了一个 `addCount` 方法，这个 `addCount` 方法里做了扩容判断，
键值对个数累加等操作（看不进去，太多变量不知道什么意思）。

`putVal` 方法主要过程总结：

1. 根据 key 的哈希值计算对应的桶位置
2. 判断当前桶位置上是否有键值对，如果没有通过 CAS 直接插入
3. 哈希冲突：加锁（头节点），判断是树结构还是链表结构，然后分别走不同的流程
4. 以链表为例：从头开始遍历，如果 key 已经存在，根据 onlyIfAbsent 判断是否覆盖原来的 value 值，如果不存在则在尾节点继续插入一个新节点
5. 计数，并判断是否需要扩容

### 三、get 方法

``` java
    public V get(Object key) {
        Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
        // 计算哈希值
        int h = spread(key.hashCode());
        // 哈希表存在，长度大于 0，桶位置上有键值对
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (e = tabAt(tab, (n - 1) & h)) != null) {
            // 如果头节点对应的 key 与查找 key 是同一个就直接返回
            if ((eh = e.hash) == h) {
                if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                    return e.val;
            }
            // TODO 哈希值小于 0？
            else if (eh < 0)
                return (p = e.find(h, key)) != null ? p.val : null;
            while ((e = e.next) != null) {
                // 遍历链表，找到返回
                // TODO 树与链表如何区分？HashMap 会判断链表、树节点后分别走对应的查找
                if (e.hash == h &&
                    ((ek = e.key) == key || (ek != null && key.equals(ek))))
                    return e.val;
            }
        }
        return null;
    }
```

待续...