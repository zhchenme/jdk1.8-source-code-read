### 一、知识点梳理

- `HashMap` 的初始化是通过 `resize()` 方法完成的，一般在构造函数中初始化加载因子，在 `resize()` 方法中初始化默认大小与扩容阈值
- 在扩容时处理链表并非处理单个节点，而是整个桶位置上的所有节点一同处理
- 链表转成树（哈希表容量达到 64 个，链表长度 8 个）的过程是先把所有链表节点转成树节点，然后再进行树化
- `resize()` 方法中，在对原哈希表扩容（容量与扩容阈值翻倍）之后，进行了一次判断 `(oldThr > 0)`，然后执行了 `newCap = oldThr`，想不明白这一步有什么作用？
- 线程安全性与红黑树先占个坑

对于上面的疑问，i know why.上代码，下面是 `resize()` 方法中有疑问的地方：

``` java
        // 哈希表已存在
        if (oldCap > 0) {
            // 如果哈希表容量已达最大值，不进行扩容，并把阈值置为 0x7fffffff，防止再次调用扩容函数
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 新容量为原来数组大小的两倍
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                // 把新的扩容阈值也扩大为两倍
                newThr = oldThr << 1; // double threshold
        }
        // TODO 为什么把新哈希表容量置为老的扩容阈值？
        // 哈希表不存在，哈希表还没有初始化
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 初始化哈希表，初始化容量为 16，阈值为 0.75 * 16
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
```

在没有初始化哈希表的时候，`threshold` 并不是扩容阈值，而是哈希表初始容量大小，可以在 `threshold` 的注释中对应这句话，光说没有说服力，下面看代码

``` java 
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }
```
上面是 `HashMap` 的一个构造函数，可以看到调用了 `tableSizeFor(initialCapacity)` 方法初始化 `threshold`，下面接着看

``` java
    static final int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
```
上面的方法用于返回大于 `cap` 的最小的 2 的幂，也就是哈希表初始容量大小。bingo~

### 二、添加键值对执行过程

show me the code ...

``` java
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        /**
         * tab：哈希表数组
         * p：槽中的节点
         * n：哈希表数组大小
         * i：下标（槽位置）
         */
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        // 当哈希表数组为 null 或者长度为 0 时，初始化哈希表数组
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 没有出现哈希碰撞直接新节点插入对应的槽内
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        // 哈希碰撞
        else {
            Node<K,V> e; K k;
            // 如果 key 已经存在，记录存在的节点
            // TODO 为什么多了一步头节点 key "存在"判断？
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            // 如果是树节点，走树节点插入流程
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            // 链表处理流程
            else {
                for (int binCount = 0; ; ++binCount) {
                    // 在链表尾部插入新节点，注意 jdk1.8 中在链表尾部插入新节点
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        // 如果当前链表中的元素大于树化的阈值，进行链表转树的操作
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    // 如果 key 已经存在，直接结束循环
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    // 重置 p 用于遍历
                    p = e;
                }
            }
            // 如果 key 重复则更新 key 值
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                // 更新当前 key 值
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        // 如果键值对个数大于阈值时（capacity * load factor），进行扩容操作
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }
```

### 三、扩容机制

上代码
``` java
    final Node<K,V>[] resize() {
        // 用于记录老的哈希表
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        // 哈希表已存在
        if (oldCap > 0) {
            // 如果哈希表容量已达最大值，不进行扩容，并把阈值置为 0x7fffffff，防止再次调用扩容函数
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 新容量为原来数组大小的两倍
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                // 把新的扩容阈值也扩大为两倍
                newThr = oldThr << 1; // double threshold
        }
        // TODO 为什么把新哈希表容量置为老的扩容阈值？
        // 如果执行下面的代码，表示哈希表还没有初始化，在没有初始化的时候 threshold 为哈希表初始容量大小，这样就可以理解了，biu~
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 初始化哈希表，初始化容量为 16，阈值为 0.75 * 16
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        // 阈值为 0 额外处理
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        // 重置阈值与哈希表
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        /*------------------------------ 以上为新哈希表分配容量，以下为元素 rehash ------------------------------------*/
        if (oldTab != null) {
            // 遍历当前哈希表，将当前桶位置的键值对（链表或树或只有一个节点）赋值到新的哈希表中
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    // rehash 之前记录链表后直接置 null，让 GC 回收
                    oldTab[j] = null;
                    // 如果当前桶位置上只有一个元素，直接进行 rehash
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    // 处理树节点
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    // 处理链表节点
                    else { // preserve order
                        /**
                         * 条件：原哈希表大小为 16，扩容后的哈希表大小为 32
                         *
                         * 1.假设某个 key 的哈希值为 17，那么它在原来哈希表中的桶位置为 1，在新的哈希表中的桶位置也为 17
                         * 通过 ((e.hash & oldCap) == 0) 判断条件不成立，rehash 时通过 newTab[j + oldCap] = hiHead 赋值，保证其位置正确性
                         * 2.假设某个 key 的哈希值为 63，那么它在原来哈希表中的桶位置为 1，在新的哈希表中的桶位置也为 1
                         * 通过 ((e.hash & oldCap) == 0) 判断条件成立，通过 newTab[j] = loHead 赋值，保证其位置正确性
                         */
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        // 遍历当前桶位置上的所有节点
                        do {
                            next = e.next;
                            /**
                             * 既可以使元素均匀的分布在新的哈希表中，又可以保证哈希值的正确性（比如 get(key) 操作）
                             * (e.hash & oldCap) 计算的不是在老哈希表中的桶位置，这样计算可以使数据均匀的分布在新的哈希表中
                             */
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        // 下面为新的哈希表赋值（移动整个链表）
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }
```

### 四、频繁调用的方法

获取节点

``` java
    /**
     * 根据 key 获取 对应的节点
     * 1.根据 key 的哈希码计算出对应的桶位置
     * 2.判断是否为头节点、树节点、链表后进行查找
     *
     * @param hash hash for key
     * @param key the key
     * @return the node, or null if none
     */
    final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        // 进行判断并通过 tab[(n - 1) & hash] 计算当前 key 在哈希表中的位置
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & hash]) != null) {
            // 如果是当前桶位置上的头节点直接返回
            if (first.hash == hash && // always check first node
                ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            // 如果不是头节点不是要找的节点，判断是树节点还是链表节点后继续查找
            if ((e = first.next) != null) {
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                // 链表、从头节点开始遍历查找
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```

移除节点

``` java 
    /**
     * Implements Map.remove and related methods
     *
     * 移除键值对并返回
     * 注意：很多地方在判断 key 是否相同时都先判断哈希码是否相同，再接着进行其他的条件判断
     *
     * @param hash hash for key                                       key 的哈希码
     * @param key the key                                             key 值
     * @param value the value to match if matchValue, else ignored    对应的 value 值
     * @param matchValue if true only remove if value is equal        移除时是否判断 value
     * @param movable if false do not move other nodes while removing 移除键值对时是否移动其他节点
     * @return the node, or null if none
     */
    final Node<K,V> removeNode(int hash, Object key, Object value,
                               boolean matchValue, boolean movable) {
        Node<K,V>[] tab; Node<K,V> p; int n, index;
        // 判断，并根据哈希码计算出对应的桶位置 p 头节点
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (p = tab[index = (n - 1) & hash]) != null) {
            Node<K,V> node = null, e; K k; V v;
            // 判断当前 key 对应的是否头节点，如果是直接记录头节点，因为还有判断条件需要执行（matchValue ..）
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                node = p;
            // 如果不是头节点就判断是链表还是树
            else if ((e = p.next) != null) {
                if (p instanceof TreeNode)
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                else {
                    do {
                        if (e.hash == hash &&
                            ((k = e.key) == key ||
                             (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        p = e;
                    } while ((e = e.next) != null);
                }
            }
            /* -------------------- 上面的代码用于找到要删除的节点，下面进行删除与判断（是否判断 value 也相同） --------------- */
            if (node != null && (!matchValue || (v = node.value) == value ||
                                 (value != null && value.equals(v)))) {
                /**
                 * 1.树节点
                 * 2.链表头节点
                 * 3.链表非头节点
                 *
                 */
                if (node instanceof TreeNode)
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    tab[index] = node.next;
                else
                    p.next = node.next;
                ++modCount;
                // size - 1
                --size;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }
```


