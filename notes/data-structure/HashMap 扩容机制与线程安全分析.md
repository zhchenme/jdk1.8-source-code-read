如果大家有仔细阅读过 `HashMap` 的源码就会发现 `HashMap` 的哈希表初始化并不是在其构造函数中进行的，而是 `resize()` 方法。

这篇文章不对 `HashMap` 中的树进行介绍。

### 一、HashMap 四个构造函数

这里把 `HashMap` 的四个构造函数全贴出来，主要是给大家一个参照。

PS：并不是所有的构造函数都初始化了 `threshold`，但是所有的构造函数都初始化了加载因子，另外初始容量大小也都没有初始化。

```java
    // 构造函数 1 
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
    
    // 构造函数 2
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }
    
    // 构造函数 3
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; 
    }
    // 构造函数 4
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }
```

### 二、put 方法

我们都知道 `HashMap` 的底层是一个基于 `Node<K,V>[] table` 的数组，看完了上面的构造函数，我们发现数组并不是在构造函数中完成的，那是在哪里初始化的呢？带着这个疑问我们来看一下 `HashMap` 中的 `put` 方法。

```java
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * Implements Map.put and related methods
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent 为 true 时不改变已经存在的值
     * @param 为 false 时表示哈希表正在创建
     * @return previous value, or null if none
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        /**
         * tab：哈希表数组
         * p：桶位置上的头节点
         * n：哈希表数组大小
         * i：下标（槽位置）
         */
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        // 当哈希表数组为 null 或者长度为 0 时，初始化哈希表数组
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 头节点为空直接插入（无哈希碰撞）
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        // 哈希碰撞
        else {
            Node<K,V> e; K k;
            // 与头节点发生哈希冲突，进行记录
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
                    // 如果 key（非头节点）已经存在，直接结束循环
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    // 重置 p 用于遍历
                    p = e;
                }
            }
            // 如果 key 已经存在则更新 value 值
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

主要流程总结：
 1. 哈希表是否初始化判断
 2. 是否发生哈希碰撞，（无）头节点判断：如果头节点不存在直接插入当前键值对
 3. key 是否存在条件判断：头节点冲突进行记录、树形态与链表形态分别走对应的流程插入键值对
 4. 链表处理，如果插入的 key 在链表中不存在，则在链表尾部插入键值对（后判断是否需要转树），如果发生冲突，则记录当前冲突节点
 5. key 存在，新的 value 覆盖旧的 value，并返回旧 value
 6. 是否需要扩容

### 三、扩容机制，resize 方法

PS：`resize()` 方法并不只是用于扩容，还用于初始化哈希表。

```java
    final Node<K,V>[] resize() {
        // 用于记录老的哈希表
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        // 哈希表已存在
        if (oldCap > 0) {
            // 如果哈希表容量已达最大值，不进行扩容，并把阈值置为 0x7fffffff
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 新容量为原来数组大小的两倍
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                // 把新的阈值也扩大为两倍
                newThr = oldThr << 1; // double threshold
        }
        // 初始化哈希表数组，对应初始化 threshold 的构造函数
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 初始化哈希表数组，对应没有初始化 threshold 的构造函数
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        // 阈值为 0 处理（哈希表还没有初始化但 threshold 已经被初始化）
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        // 重置（初始化）阈值与哈希表
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        
        /*------------- 下面是 rehash 过程 ---------------*/
        
        if (oldTab != null) {
            // 遍历老哈希表，将当前桶位置的键值对移动到新的哈希表中
            for (int j = 0; j < oldCap; ++j) {
                // 记录当前桶位置的头节点
                Node<K,V> e;
                // 头节点判是否为 null
                if ((e = oldTab[j]) != null) {
                    // 置 null（链表或树），让 GC 回收
                    oldTab[j] = null;
                    // 如果当前桶位置上只有一个元素，直接 rehash 到新的哈希表中
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    // 处理树节点
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    // 处理链表节点，下面这个过程的实现很巧妙，我把它单独拿出来分析
                    else { // preserve order
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
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
链表 rehash 过程：
```java
{
    else { // preserve order
        /**
        * rehash 时分高低位处理
        */
        Node<K,V> loHead = null, loTail = null;
        Node<K,V> hiHead = null, hiTail = null;
        Node<K,V> next;
        // 遍历链表
        do {
            next = e.next;
            /**
            * 判断旧哈希表桶位置上的所有元素位于低位还是高位
            * e.hash & oldCap 计算的不是桶位置
            * e.hash & (oldCap -1) 计算的才是桶位置
            *
            * 关于高低位我们举个来帮助例子：
            * 条件：原哈希表大小为 16，扩容后的哈希表大小为 32
            * 
            * 1.假设 key1.hashCode = 15，位于旧的哈希表 15 桶位置上
            * key1.hash & oldCap = 15 & 16 
            * 0000 1111 & 0001 0000 = 0000 0000 第 5 位为 0
            * key1 在新的哈希表中的位置也是 15 桶位置上
            *
            * 2.假设 key2.hashCode = 17，位于旧的哈希表 1 桶位置上
            * key2.hash & oldCap = 17 & 16 
            * 0001 0001 & 0001 0000 = 0001 0000 第 5 位 为 1
            *
            * 为什么要以第 5 位为标准呢？
            * 因为老得容量为16，在计算哈希值时，高于第四位的就没有计算的必要了
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
        // 上面举的例子看懂了，这里就很好理解了，低位（0）位置保持不变直接 rehash
        if (loTail != null) {
            loTail.next = null;
            newTab[j] = loHead;
        }
        // 高位（1）位置需要加上老哈希表的容量
        if (hiTail != null) {
            hiTail.next = null;
            newTab[j + oldCap] = hiHead;
        }
}
```

主要流程总结： 

 1. 判断哈希表是否初始化（已经初始化、`threshold` 已经初始化、`threshold` 没有初始化）
 2. 当 `threshold` 没有初始化时初始化 `threshold`
 3. 遍历旧的哈希表，进行 rehash，如果桶位置上没有键值对则直接略过，如果只有一个节点，直接 rehash 到新的哈希表中
 4. 树与链表形态判断，分别走对应的 rehash 流程
 5. 链表通过高低位方式 rehash

### 四、并发安全
 
 jdk1.7 中的 `HashMap` 在扩容时新哈希表数组和旧哈希表数组之间存在相互引用关系（我并没有仔细看过，有兴趣的可以阅读一下），因此在并发情况下会出现死循环的问题。在 jdk1.8 中是否还存在同样的问题？下面我们通过一个例子进行验证一下。
 
 ```java
 public class HashMapConcurrentTest {
     /**
      * NUMBER = 50，表示 50 个线程分别执行 put 方法 50 次
      * 线程安全的情况下因该 map size 应该为 2500
      */
     public static final int NUMBER = 50;
     public static void main(String[] args) {
         Map<String, String> map = new HashMap<>();
         for (int i = 0; i < NUMBER; i++) {
             new Thread(new HashMapTask(map)).start();
         }
         System.out.println("map size = " + map.size());
     }
 }
 
 class HashMapTask implements Runnable {
 
     Map<String, String> map;
 
     public HashMapTask(Map<String, String> map) {
         this.map = map;
     }
 
     @Override
     public void run() {
         for (int i = 0; i < HashMapConcurrentTest.NUMBER; i++) {
             map.put(i + "-" + Thread.currentThread().getName(), "test");
         }
     }
 }
 ```
 其中一个执行结果截图：
 ![在这里插入图片描述](https://img-blog.csdnimg.cn/20181218113731592.png)
 上面开了 50 个线程往 `HashMap` 中添加元素，每个线程执行 50 次 `put` 方法，在线程安全的情况下，`map` 中应该有 2500 个键值对，但是执行的结果大都是小与 2500 的（并不会产生死循环）。
 
 jdk1.8 中的 `HashMap` 新老数组之间不存在了引用关系，因此不会出现死循环的情况，但是却会存在键值对丢失的现象。为什么会出现键值对丢失的现象呢？下面以链表为例来简单分析一下（个人理解，不正确的话还请大家指正）。
 
 多线程情况下，可能会有多个线程进入 `resize` 方法，假设第一个线程进入了 `resize` 方法，在处理链表时会先记录一下，然后直接将对应的旧哈希表数组中的链表置 `null`，此时第二个线程进来了，因为上一个线程已经把链表置 `null` 了，线程 2 判定当前桶位置上没有键值对，如果线程 2 返回的哈希表数组覆盖了线程 1 的哈希表数组，就会丢失一部分因线程 1 置 `null` 的键值对。
 
 ```java
    ...
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    ...
        }               
 ```
 
 ### 参考文章
 
 [HashMap在JDK1.8中并发操作，代码测试以及源码分析](https://www.cnblogs.com/wenbochang/p/9425541.html)