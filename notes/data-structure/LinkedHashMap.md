一张图看懂 `LinkedHashMap`...

![](http://phtpnyqb4.bkt.clouddn.com/linkedhashmap)<br>

### 一、HashMap & LinkedHashMap

#### 1.1 `containsValue` 方法对比
- `HashMap` 遍历哈希表中的所有桶，然后遍历桶位置上的所有节点
- `LinkedHashMap` 从头节点遍历，一直遍历到尾节点

`HashMap` 中代码如下：
``` java
    public boolean containsValue(Object value) {
        Node<K,V>[] tab; V v;
        if ((tab = table) != null && size > 0) {
            // 遍历哈希表
            for (int i = 0; i < tab.length; ++i) {
                // 遍历当前桶上的所有节点
                // TODO 为什么没有对节点类型进行判断，分别走对应的查找？
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    if ((v = e.value) == value ||
                        (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }
```

`LinkedHashMap` 中代码如下：
``` java
    public boolean containsValue(Object value) {
        /**
         * 遍历双向链表，判断 value，与 HashMap 完全不同
         */
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }
```

#### 1.2 `LinkedHashMap` 如何维持双向链表

`LinkedHashMap` 中增删改方法都是复用 `HashMap` 的，那么又是怎么维持一个双向链表的呢？

我们以添加键值对为例，在 `HashMap` 中的 `putVal` 方法中，通过新建节点对象的形式添加键值对，以链表节点为例，分别看一下它们的 `newNode` 方法有什么不同。

`HashMap` 中的 `newNode` 方法：
``` java
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }
```

`LinkedHashMap` 中的 `newNode` 方法：
``` java
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        // 初始化 LinkedHashMap 键值对 Entry
        LinkedHashMap.Entry<K,V> p =
            new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        // 这一步很重要，添加的键值对都会被添加到 LinkedHashMap 尾部，因此可以维持一个双向链表
        linkNodeLast(p);
        return p;
    }
```

`linkNodeLast` 方法具体实现：
``` java
    private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
        // 获取并记录 tail 节点
        LinkedHashMap.Entry<K,V> last = tail;
        // 重置 tail
        tail = p;
        if (last == null)
            head = p;
        else {
            // 将节点连接起来，构成双向链表
            p.before = last;
            last.after = p;
        }
    }
```

看了上面的方法我们就知道为什么 `LinkedHashMap` 公用 `HashMap` 的方法也能维持一个双向链表了。

#### 1.3 `LinkedHashMap` 迭代键值对与 `HashMap` 的对比

相关迭代器中的迭代方法。

`HashMap` 中的 `nextNode()` 方法：
 ``` java
     final Node<K,V> nextNode() {
          // 记录哈希表数组
         Node<K,V>[] t;
         Node<K,V> e = next;
         if (modCount != expectedModCount)
             throw new ConcurrentModificationException();
         if (e == null)
              throw new NoSuchElementException();
          // 过滤掉没有键值对的桶位置
         if ((next = (current = e).next) == null && (t = table) != null) {
              do {} while (index < t.length && (next = t[index++]) == null);
         }
          // 下一个有键值对的桶（单个节点、树节点或链表）
         return e;
     }
 ```
`LinkedHashMap` 中的 `nextNode()` 方法：
``` java
    final LinkedHashMap.Entry<K,V> nextNode() {
        LinkedHashMap.Entry<K,V> e = next;
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        if (e == null)
            throw new NoSuchElementException();
        current = e;
        // next 为双向链表的下一个节点
        next = e.after;
        return e;
    }
```

PS：
在 `HashMap` 中有很多空方法，在某些方法中又调用这些空方法，你不要感到奇怪，它们一般是为 `LinkedHashMap` 准备的。