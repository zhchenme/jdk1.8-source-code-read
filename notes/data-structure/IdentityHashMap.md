在讲这个数据结构之前，我们先来看一段代码：

``` java
    public static void main(String[] args) {
        IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
        map.put("Hello " + "World", 1);
        map.put("Hello World", 2);
        map.put(new String("Hello World"), 3);
        System.out.println(map);
    }
```

没有仔细了解过 `IdentityHashMap` 话，很多人应该都认为会输出 `{Hello World=3}`，但实际上输出的是 `{Hello World=3, Hello World=2}`。原因在于这个类是通过对象引用来判断 key 是否相同的。我们以添加键值对为例，对判断键是否存在的情况拿出来对比下大家就知道为什么了：

- `IdentityHashMap`：`item == k`
- `HashMap`：`p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k)))`


### 一、IdentityHashMap 概述

上面我们说了 `IdentityHashMap` 是通过引用相等判断是否为同一个 key 的，除了这个区别外，`IdentityHashMap` 与 `HashMap` 最大的一个区别在于其内部数据结构，`IdentityHashMap` 底层实现是一个数组，意味着 key 与 value 都存储在数组中，因为其实现没有链表，因此是通过线性探测的方式来解决哈希冲突。


下面是一个其内部原理图：

![在这里插入图片描述](https://img-blog.csdnimg.cn/2019032018003760.png)

因为它本身还是一个存储键值对的数据结构，那知识点无外乎就是哈希函数，哈希冲突，扩容等知识点，下面我们就针对这些问题来分析一下内部源码。

### 二、IdentityHashMap 源码分析

**2.1 内部属性**

``` java
    // 存储键值对的哈希表数组
    transient Node<K,V>[] table;
    // 存储的键值对个数
    transient int size;
    // 扩容阈值
    int threshold;
```

与 `HashMap` 不同的是 `IdentityHashMap` 并没有加载因子这个概念，因为这个加载因子是固定值，下面我们在构造函数中会看到。

**2.2 构造函数**

默认的构造函数：

``` java
    public IdentityHashMap() {
        init(DEFAULT_CAPACITY);
    }
```

`DEFAULT_CAPACITY` 表示默认情况下的数组初始化大小，这个值是 32。默认的构造函数中调用了一个 `init` 方法，我们接着看这个方法。

``` java
    private void init(int initCapacity) {
        // 指定扩容阈值为初始化容量的 2/3
        threshold = (initCapacity * 2)/3;
        // 初始化哈希表数组为指定容量的 2 倍，一般空间存储 key 一半空间存储 value
        table = new Object[2 * initCapacity];
    }
```

`init` 方法中初始化了扩容阈值与哈希表大小，在计算初始值的时候都会先 * 2，就是因为它本身特殊的数组结构导致的。

`IdentityHashMap` 还提供了一个可以指定默认初始化大小的构造函数，我们知道 `HashMap` 在指定默认初始化大小时会取大于等于当前初始化大小的第一个 2 的幂作为哈希表大小。

但是 `IdentityHashMap` 内部并不是这样，`IdentityHashMap`  在计算哈希表容量时，计算出的值也是 2 的幂，但是这个值并不是严格意义上为第一个大于等于初始值的 2 的幂，有时候会大于这个值，为什么会这样呢？其实这还是和它的底层数据结构有关，因为 key 与 value 都公用一个数组，为了能存储更多的键值对，往往会把哈希表容量计算的大一些。有兴趣的可以看一下，这里就不贴出来了。

**2.3 hash 方法**

我们来想一个问题：因为 key 与 value 都存在一个数组中，那么 key 与 value 的位置要怎么存储呢，奇数位置可以放 key 吗？答案是不能的，如果奇数位置上放 key 的话，那 0 位置上放的就是 value，这样做是不合理的。为了保证正确性，偶数位置上放的一定是 key 然后奇数位置上放 value。下面我们就来看一下是不是这样的。

``` java
    private static int hash(Object x, int length) {
        // 此方法不管你是否重写了 hashCode 方法都会返回对象默认的哈希值
        // 即使重写了 hashCode 也不会调用重写的 hashCode 方法
        int h = System.identityHashCode(x);
        // Multiply by -127, and left-shift to use least bit as part of hash
        return ((h << 1) - (h << 8)) & (length - 1);
    }
```

我们来分析一下 `((h << 1) - (h << 8)) & (length - 1)` 的运算，首先哈希值左移一位，保证是一个偶数，减去一个左移八位的值也是一个偶数，然后与上全 1 的二进制，计算出的还是一个偶数，这样就能保证每个 key 计算出的索引一定是个偶数，然后 value 放在索引值下一个位置上就可以了。

**2.4 put 方法**

``` java
    public V put(K key, V value) {
        // key == null ? new Object() : key
        // 如果 key 为 null 那么用一个内部的 Object 对象来代替 null
        Object k = maskNull(key);
        // 获取哈希表与哈希表大小
        Object[] tab = table;
        int len = tab.length;
        // 计算哈希值，len 用于计算在数组中的索引
        int i = hash(k, len);

        Object item;
        // 这里分为两种情况，哈希冲突与非冲突，当冲突时会线性探测寻找下一个可用的位置
        while ((item = tab[i]) != null) {
            // key 引用相同，值覆盖，返回老得值
            if (item == k) {
                // 获取老的 value
                V oldValue = (V) tab[i + 1];
                // 更新值
                tab[i + 1] = value;
                return oldValue;
            }
            // 哈希冲突，计算下一个 key 角标
            i = nextKeyIndex(i, len);
        }

        modCount++;
        // 设置 key 与 value
        tab[i] = k;
        tab[i + 1] = value;
        // 判断是否需要扩容，此时的 len 的大小为 capacity 的两倍，可以在构造函数中查看
        if (++size >= threshold)
            resize(len);
        return null;
    }
```

添加过程还是比较简单的，根据 key 的哈希值计算在数组中的索引（一定是个偶数），然后判断当前数组位置上是否已经存在了 key，当然存在也分为两种情况，一种是 key 引用相同，另外一种情况是 key 引用不同，不同时意味着哈希冲突，因此就要找下一个可用的位置来存储键值对，最后添加键值对后判断是否需要扩容。

当哈希冲突时调用了 `nextKeyIndex` 方法来寻找下一个可用的位置，下面我们接着看下这个方法。

``` java
    private static int nextKeyIndex(int i, int len) {
        return (i + 2 < len ? i + 2 : 0);
    }
```

为什么 i 要加 2 呢，其实还是为了保证 key 在偶数索引上，如果一直找到数组尾部还没有找到可用的位置，那么会从头开始继续寻找，可以理解为一个环状的数组，这点和 `ThreadLocalMap` 有点相似，有兴趣的可以自己看下 `ThreadLocal` 的源码。

**2.5 resize 方法**

在添加键值对过后会判断当前数组中的键值对个数是否大于等于扩容的阈值，如果大于等于那么久对数组进行扩容，下面是扩容的方法。

``` java
    private void resize(int newCapacity) {
    	// 新数组容量为原来的 2 倍
        int newLength = newCapacity * 2;

        Object[] oldTable = table;
        int oldLength = oldTable.length;
        // 判断不能无限扩容
        if (oldLength == 2*MAXIMUM_CAPACITY) { // can't expand any further
            if (threshold == MAXIMUM_CAPACITY-1)
                throw new IllegalStateException("Capacity exhausted.");
            threshold = MAXIMUM_CAPACITY-1;  // Gigantic map!
            return;
        }
        if (oldLength >= newLength)
            return;

        Object[] newTable = new Object[newLength];
        /*
        * newLength / 3 = （newCapacity * 2）/3
        * 只不过换了种表现形式
        */
        threshold = newLength / 3;

        // 键值对 rehash
        for (int j = 0; j < oldLength; j += 2) {
            Object key = oldTable[j];
            if (key != null) {
                Object value = oldTable[j+1];
                // 下次 GC 回收
                oldTable[j] = null;
                oldTable[j+1] = null;
                // 根据 key 计算桶位置
                int i = hash(key, newLength);
                // 从当前桶位置向后遍历，找出空位置，存放当前键值对
                while (newTable[i] != null)
                    i = nextKeyIndex(i, newLength);
                // key value 赋值
                newTable[i] = key;
                newTable[i + 1] = value;
            }
        }
        // 重置 table
        table = newTable;
    }
```

扩容的过程并不复杂，首先对哈希表数据进行扩容，然后重置扩容阈值，接着对元素 rehash，rehash 的过程也要考虑哈希冲突的情况，最后重置哈希表数组就结束了。

**2.6 get 方法**

``` java
    public V get(Object key) {
        // 键判断
        Object k = maskNull(key);
        Object[] tab = table;
        int len = tab.length;
        // 根据 key 计算角标
        int i = hash(k, len);
        while (true) {
            Object item = tab[i];
            // key 相同返回 value
            if (item == k)
                return (V) tab[i + 1];
            // 如果找不到会一直向后查找，直到数组索引位置上没有 key 时停止
            // 为什么 key 为 null 时停止查找呢，原因是，如果没有出现哈希冲突，那么根据 key 的哈希值一次就可以定位到要返回的 value
            // 如果出现了哈希冲突，那么该键值与前面冲突的键值对之间一定是连续的
            if (item == null)
                return null;
            // 计算键位置，跳过 value，接着查找
            i = nextKeyIndex(i, len);
        }
    }
```

如果我们不看源码的话，让大家猜一下这个查找的过程是怎么样的，大家能猜到吗？相对来说查找就比较简单一些了，先根据 key 计算出对应的数组索引位置，判断 key 是否是同一个，如果是直接返回，如果不是意味着可能出现哈希冲突，当然也可能是不存在该 key，为了保证正确性，还会向后面查找，当出现 null 时即结束查找，原因上面作了解释。

**2.7 remove 方法**

下面我们来看一下 `remove` 方法，变相来说删除其实也就是查找，只不过多了一个删除动作，查找过程与 `get` 方法类似，多了一个重要的操作，把删除的键值对置 `null` 后需要从当前位置开始调整哈希表，调整哈希表是为了纠正因为哈希冲突导致的不正确性。

``` java
    public V remove(Object key) {
        Object k = maskNull(key);
        Object[] tab = table;
        int len = tab.length;
        // 根据 key 的哈希值计算索引
        int i = hash(k, len);

        // 这个过程与 get 方法类似
        while (true) {
            Object item = tab[i];
            if (item == k) {
                modCount++;
                size--;
                @SuppressWarnings("unchecked")
                    V oldValue = (V) tab[i + 1];
                // 下次 GC 回收
                tab[i + 1] = null;
                tab[i] = null;
                // 删除键值对后需要调整哈希表数组
                // 为什么要调整哈希表数组呢，原因是因为后面键值对可能是因为哈希冲突添加进去的，
                // 如果当前键值对移除了，那么后面因为哈希冲突添加的键值对就不能通过 get 方法获取了
                closeDeletion(i);
                return oldValue;
            }
            if (item == null)
                return null;
            i = nextKeyIndex(i, len);
        }
    }
```

上面的源码中我们对调整哈希表做了分析，下面就来看下具体是怎么做的。

``` java
    private void closeDeletion(int d) {
        // Adapted from Knuth Section 6.4 Algorithm R
        Object[] tab = table;
        int len = tab.length;
        Object item;
        /**
         * 因为移除的键值对的索引并不一定是其哈希值算出的桶位置
         *（线性探测，向后移动或从 0 开始） ，所以需要对哈希表进行调整
         *
         * 结束循环的条件是 key 为 null
         */
        for (int i = nextKeyIndex(d, len); (item = tab[i]) != null;
             i = nextKeyIndex(i, len) ) {
            // 当前桶位置计算出 key 的哈希值，这个哈希值并不一定等于 i
            int r = hash(item, len);
            /**
             * d：当前 key 所在的索引位置
             * r：下一个 key 本来应该存放的位置
             * i：下一个 key 实际存放位置（可能移动，它的位置被占，只能向后移动，甚至可能移动到最前面）
             */
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                // 把后面的键值对移动到前面去，因为是一个环操作，当然也可能是把前面的键值对给移到后面去
                tab[d] = item;
                tab[d + 1] = tab[i + 1];
                // 移动后置 null
                tab[i] = null;
                tab[i + 1] = null;
                // 重置 d，以便处理后续的键值对
                d = i;
            }
        }
    }
```

调整的过程其实就是键值对向前移动，当然数组头位置的元素也可能移动到数组尾部，结束调整循环的条件是 key 为 `null`，我想到这里大家应该都知道了为什么要以 key 为 null 作为结束条件了。


### 三、IdentityHashMap 总结

- 它是一个很特殊的类，`IdentityHashMap` 采用引用相等来判断 key 是否相同，即内存地址相同。它只在一些场景适用，比如：序列化、深度拷贝、维护代理对象等                                                                                                                                      
- `IdentityHashMap` 没有继承自 `HashMap`，本身提供了增删该查的功能
- `IdentityHashMap` 底层数据结构是数组（键值对都存放在数组中），默认初始容量 32，但是初始化的时候需要将底层的数组容量设置成 64，一半用于存储 key，一半用于存储 value
- `IdentityHashMap` 的扩容阈值为 2/3 总容量
- `IdentityHashMap` 底层使用线性探测法来解决哈希冲突，即根据哈希值找到对应的位置，如果当前位置没有 key，那么就将当前 key 存储在该位置上，value 存储在下一个位置上，如果当前位置已经存在 key，那么就向后查找，直到找到空的位置用来存储键值对，如果找到底层数组尾端也没有找到空的位置，那么就接着从头开始查找

