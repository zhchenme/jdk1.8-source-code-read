一张图看懂 `IdentityHashMap` ...

[![image.png](https://i.postimg.cc/906C3yZs/image.png)](https://postimg.cc/tnNLWZrz)<br>
### 一、IdentityHashMap 相关知识点

#### 1.1 IdentityHashMap 概括

- 它是一个很特殊的类，`IdentityHashMap` 采用引用相等来判断 key 是否相同，即内存地址相同。它只在一些场景适用，比如：序列化、深度拷贝、维护代理对象等                                                                                                                                      
- `IdentityHashMap` 没有继承自 `HashMap`，本身提供了增删该查的功能
- `IdentityHashMap` 底层数据结构是数组（键值对都存放在数组中），默认初始容量 32，但是初始化的时候需要将底层的数组容量设置成 64，一半用于存储 key，一半用于存储 value
- `IdentityHashMap` 的扩容阈值为 2/3 总容量
- `IdentityHashMap` 底层使用线性探测法来解决哈希冲突，即根据哈希值找到对应的位置，如果当前位置没有 key，那么就将当前 key 存储在该位置上，value 存储在下一个位置上，如果当前位置已经存在 key，那么就向后查找，直到找到空的位置用来存储键值对，如果找到底层数组尾端也没有找到空的位置，那么就接着从头开始查找

#### 1.2 put 方法

``` java
    public V put(K key, V value) {
        // key == null ? new Object() : key
        Object k = maskNull(key);
        // 获取底层数组与大小
        Object[] tab = table;
        int len = tab.length;
        // 计算哈希值，len 用于计算桶位置
        int i = hash(k, len);

        Object item;
        // 哈希冲突，直到找到空位置或 key 引用相等直接覆盖
        while ((item = tab[i]) != null) {
            // key 引用相同（注意判断使用 ==），值覆盖，返回老值
            if (item == k) {
                // 获取老的 value
                V oldValue = (V) tab[i + 1];
                // 更新值
                tab[i + 1] = value;
                return oldValue;
            }
            // 计算下一个 key 角标
            i = nextKeyIndex(i, len);
        }

        modCount++;
        // 设置 key 与 value
        tab[i] = k;
        tab[i + 1] = value;
        // 判断是否需要扩容，此时的 len 的大小为 capacity 的两倍，可以在构造函数中查看
        if (++size >= threshold)
            resize(len); // len == 2 * current capacity.
        return null;
    }
```

通过 `nextKeyIndex(i, len)` 方法解决哈希冲突直到找到空位置（key 的位置）存放当前键值对，方法实现如下：

``` java
    /**
     * Circularly traverses table of size len.
     *
     * 计算 key 的角标，需要跳过中间的 value，当超过数组长度时从 0 开始接着循环
     * 注意并不是直接加1，因为中间搁的位置是用来存放 value 的
     */
    private static int nextKeyIndex(int i, int len) {
        return (i + 2 < len ? i + 2 : 0);
    }
```

#### 1.3 resize(int newCapacity) 方法

``` java
    private void resize(int newCapacity) {
        // assert (newCapacity & -newCapacity) == newCapacity; // power of 2
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

        /**
         * 如果初始容量为 32，则 len 为 64，threshold = 2/3 * 32
         * 扩容后新的容量为 64，则 len 是 128，threshold = 128 / 3
         */
        Object[] newTable = new Object[newLength];
        /*
        * 设置阈值为新容量的 2/3， newLength = 2 * newCapacity，因此只要算三分之一即可
        */
        threshold = newLength / 3;

        for (int j = 0; j < oldLength; j += 2) {
            Object key = oldTable[j];
            if (key != null) {
                Object value = oldTable[j+1];
                // GC 回收
                oldTable[j] = null;
                oldTable[j+1] = null;
                // 根据 key 计算桶位置
                int i = hash(key, newLength);
                // 从当前桶位置向后遍历，找出空位置，存放当前键值对
                while (newTable[i] != null)
                    i = nextKeyIndex(i, newLength);
                // rehash 赋值
                newTable[i] = key;
                newTable[i + 1] = value;
            }
        }
        // 重置 table
        table = newTable;
    }
```

#### 1.4 remove(Object key) 方法

最有意思的应该是删除方法，因为在删除键值对时你不知道要删除的键值对后面是否还有因为哈希值相同被移动到后面的键值对，因此在删除元素时得维护整个哈希表数组。

``` java
    public V remove(Object key) {
        Object k = maskNull(key);
        Object[] tab = table;
        int len = tab.length;
        int i = hash(k, len);

        while (true) {
            Object item = tab[i];
            if (item == k) {
                modCount++;
                size--;
                @SuppressWarnings("unchecked")
                    V oldValue = (V) tab[i + 1];
                // 把 key 和 value 置 null，让 GC 回收
                tab[i + 1] = null;
                tab[i] = null;
                // 调整哈希表
                closeDeletion(i);
                return oldValue;
            }
            if (item == null)
                return null;
            i = nextKeyIndex(i, len);
        }

    }
```

重点来了...这个方法看了大概半个小时，才简单的知道它的原理，说不定几天后就忘了...

``` java
    private void closeDeletion(int d) {
        // Adapted from Knuth Section 6.4 Algorithm R
        Object[] tab = table;
        int len = tab.length;

        // Look for items to swap into newly vacated slot
        // starting at index immediately following deletion,
        // and continuing until a null slot is seen, indicating
        // the end of a run of possibly-colliding keys.
        Object item;
        /**
         * 因为移除的键值对的角标并不一定是其哈希值算出的桶位置（线性探测，向后移动或从 0 开始） ，所以需要对哈希表进行调整
         * 注意这里当判断 key 为 null 后直接结束了循环
         */
        for (int i = nextKeyIndex(d, len); (item = tab[i]) != null;
             i = nextKeyIndex(i, len) ) {
            // The following test triggers if the item at slot i (which
            // hashes to be at slot r) should take the spot vacated by d.
            // If so, we swap it in, and then continue with d now at the
            // newly vacated i.  This process will terminate when we hit
            // the null slot at the end of this run.
            // The test is messy because we are using a circular table.
            // 当前桶位置计算出 key 的哈希值，这个哈希值并不一定等于 i
            int r = hash(item, len);
            /**
             * r：理想的存放位置（计算哈希值直接插入数据，不移动）
             * i：实际存放位置（可能移动，它的位置被占，只能向后移动，甚至可能移动到最前面）
             * i == r：最佳存放位置
             * TODO 这里理解的不是很好
             */
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                // 把后面的键值对移动到前面去
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

PS：放一个参考链接

[https://www.imooc.com/article/22081](https://www.imooc.com/article/22081)