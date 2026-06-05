package org.example;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TestQueue<E> {
    private final Object[] underlyingArray;
    private final int capacity;
    private final ReentrantLock lock;
    private final Condition forRead;
    private final Condition forWrite;
    private int head = 0;
    private int tail = 0;
    private int cnt = 0;
    public TestQueue(int size) {
        if (size <= 0) throw new IllegalArgumentException("Invalid size");
        this.underlyingArray = new Object[size];
        this.capacity = size;
        this.lock = new ReentrantLock();
        this.forRead = lock.newCondition();
        this.forWrite = lock.newCondition();
    }

    public void put(E element) throws InterruptedException {
        if (element == null) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        try{
            while (capacity == cnt){
                forWrite.await();
            }
            enqueue(element);
            forRead.signal();
        }finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        lock.lock();
        try{
            while(cnt==0) {
                forRead.await();//что бы не грузить CPU
            }
            E e = poll();
            forWrite.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public E peek() {
        lock.lock();
        try{
            if (cnt ==0) return null;
            return (E)this.underlyingArray[head];
        }finally {
            lock.unlock();
        }

    }

    private void enqueue(E element) {
        this.underlyingArray[tail] = element;
        tail = (tail+1) % capacity;
        cnt++;
    }

    @SuppressWarnings("unchecked")
    private E poll() {
        E res = (E) this.underlyingArray[head];
        this.underlyingArray[head] = null;
        head = (head+1) % capacity;
        cnt--;
        return res;
    }

    public int getSize() {
        lock.lock();
        try {
            return cnt;
        }finally {
            lock.unlock();
        }
    }
    public boolean isEmpty() {
        return getSize() == 0;
    }
}
