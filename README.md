## jdk 源码阅读

### 一、内部数据结构

#### 1.1ArrayList 扩容过程
1：调用 `add` 方法
![Alt text](images/collection/arraylist-1.png)
2：调用 `ensureCapacityInternal` 方法
![Alt text](images/collection/arraylist-2.png)
3：调用 `ensureExplicitCapacity` 方法
![Alt text](images/collection/arraylist-3.png)
4：调用 `grow` 方法
![Alt text](images/collection/arraylist-4.png)
5：调用 `Arrays.copyOf` 方法
![Alt text](images/collection/arraylist-5.png)