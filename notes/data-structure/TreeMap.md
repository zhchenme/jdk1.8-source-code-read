### 一、TreeMap 概述

不同于 `HashMap`，`TreeMap` 底层没有那么多花里胡哨的数据结构，就是一个老大难的红黑树...

对于红黑树的理解还差很多，这里只简单的总结一些相关的知识点，后面什么时候想看了，还会把红黑树相关源码再理一遍。


`TreeMap` 底层是红黑树，每个键值对都通过 `Entry` 保存，因此没有扩容机制。下面对其中的几个方法进行总结下

### 二、put 方法

``` java
    public V put(K key, V value) {
        // 获取根节点
        Entry<K,V> t = root;
        // 没有任何节点时初始化根节点
        if (t == null) {
            compare(key, key); // type (and possibly null) check

            // 初始化根节点
            root = new Entry<>(key, value, null);
            // size = 1
            size = 1;
            modCount++;
            return null;
        }
        int cmp;
        Entry<K,V> parent;
        // split comparator and comparable paths
        Comparator<? super K> cpr = comparator;
        // 自定义比较器的情况下寻找插入位置
        if (cpr != null) {
            do {
                parent = t;
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    return t.setValue(value);
            } while (t != null);
        }
        else {
            // 没有自定义比较器
            if (key == null)
                throw new NullPointerException();
            @SuppressWarnings("unchecked")
                Comparable<? super K> k = (Comparable<? super K>) key;
            // 通过遍历找到插入的位置（父节点）
            do {
                parent = t;
                // 从根节点开始比较，如果当前 key 比父节点小则从父节点左孩子继续查找插入位置，反之从父节点右孩子查找插入位置
                cmp = k.compareTo(t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    // key 存在更新 value
                    return t.setValue(value);
            } while (t != null);
        }
        // 初始化新加入的键值对节点
        Entry<K,V> e = new Entry<>(key, value, parent);
        // 根据 key 大小判断插入左边还是右边（左右孩子）
        if (cmp < 0)
            parent.left = e;
        else
            parent.right = e;
        // 调整红黑树颜色
        fixAfterInsertion(e);
            size++;
        modCount++;
        return null;
    }
```

`put` 方法中的大部分过程都很好理解，就是根据 key 的大小寻找插入的位置，难得地方在于插入新的键值对后，红黑树的结构可能会被破坏，因此添加新的键值对后调整红黑树的结构，这一点和 `PriorityQueue` 类似。


从上面的代码中也能看出最后调用了一个 `fixAfterInsertion(e)` 方法，这个方法就是用于调整红黑树结构的。尝试着看了一下，晦涩难懂，就先放弃了，放弃了...什么时间有兴趣了再拿出来看。

``` java
private void fixAfterInsertion(Entry<K,V> x) {
        // 将新插入的节点颜色置为红色
        x.color = RED;

        while (x != null && x != root && x.parent.color == RED) {
            // 当前节点的父节点（左孩子、有孩子）进行区分
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                // 获取当前父节点（左孩子）的兄弟节点（右孩子）
                Entry<K,V> y = rightOf(parentOf(parentOf(x)));
                /**
                 * 当前节点父节点的兄弟节点颜色判断
                 */
                if (colorOf(y) == RED) {
                    // 把当前接待你的父节点设为黑色
                    setColor(parentOf(x), BLACK);
                    // 父节点的兄弟节点也设置为黑色
                    setColor(y, BLACK);
                    // 把当前节点父节点的父节点设置为红色
                    setColor(parentOf(parentOf(x)), RED);
                    // 重置节点 x，继续向上调整
                    x = parentOf(parentOf(x));
                } else {
                    // 当前节点左右孩子判断
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                Entry<K,V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        // 根节点的颜色为黑色
        root.color = BLACK;
    }
```

### 三、get 方法

``` java
    public V get(Object key) {
        // 根据 key 获取到对应的 Entry
        Entry<K,V> p = getEntry(key);
        return (p==null ? null : p.value);
    }
```

``` java 
    final Entry<K,V> getEntry(Object key) {
        // Offload comparator-based version for sake of performance
        // 基于比较器的查找
        if (comparator != null)
            return getEntryUsingComparator(key);
        // key 不允许为 null
        if (key == null)
            throw new NullPointerException();
        @SuppressWarnings("unchecked")
            Comparable<? super K> k = (Comparable<? super K>) key;
        // 记录根节点
        Entry<K,V> p = root;
        while (p != null) {
            int cmp = k.compareTo(p.key);
            // 判断从左孩子还是右孩子继续查找
            if (cmp < 0)
                p = p.left;
            else if (cmp > 0)
                p = p.right;
            else
                return p;
        }
        return null;
    }
```

相对于 `put` 方法，`get` 方法相对来说就比较简单了，就是从根节点开始判断遍历，找到了就返回，找不到返回 `null`。
 
### 四、remove 方法

还有一个方法调用后需要调整红黑树的结构，那就是 `remove` 方法，同样没看...
 
``` java
    public V remove(Object key) {
        // 获取到对应的 Entry
        Entry<K,V> p = getEntry(key);
        if (p == null)
            return null;

        V oldValue = p.value;
        // 删除该节点
        deleteEntry(p);
        return oldValue;
    }
```

先放出来代码

``` java
    private void deleteEntry(Entry<K,V> p) {
        modCount++;
        size--;

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if (p.left != null && p.right != null) {
            Entry<K,V> s = successor(p);
            p.key = s.key;
            p.value = s.value;
            p = s;
        } // p has 2 children

        // Start fixup at replacement node, if it exists.
        Entry<K,V> replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent;
            if (p.parent == null)
                root = replacement;
            else if (p == p.parent.left)
                p.parent.left  = replacement;
            else
                p.parent.right = replacement;

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = p.right = p.parent = null;

            // Fix replacement
            if (p.color == BLACK)
                fixAfterDeletion(replacement);
        } else if (p.parent == null) { // return if we are the only node.
            root = null;
        } else { //  No children. Use self as phantom replacement and unlink.
            if (p.color == BLACK)
                fixAfterDeletion(p);

            if (p.parent != null) {
                if (p == p.parent.left)
                    p.parent.left = null;
                else if (p == p.parent.right)
                    p.parent.right = null;
                p.parent = null;
            }
        }
    }
``` 

### other

- `TreeMap` 不允许 key 为 null，但是 value 可以为 null
- put、remove、迭代...