package org.example;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TestStack<E> {
    private final int capacity;
    private final Object[] underlyingArray;
    private int currentIdx;
    private final ReentrantLock lock;
    private final Condition readyForRead;
    private final Condition readyForWrite;
    public TestStack(int size) {
        if (size <= 0) throw new IllegalArgumentException("Invalid size");
        this.capacity = size;
        underlyingArray = new Object[size];
        currentIdx = 0;
        lock = new ReentrantLock();
        readyForRead = lock.newCondition();
        readyForWrite = lock.newCondition();
    }
    public void push(E element) throws InterruptedException {
        if (element == null) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        try {
            while(currentIdx >= capacity) {
                readyForWrite.await();
            }
            underlyingArray[currentIdx++] = element;
            readyForRead.signal();
        } finally {
            lock.unlock();
        }
    }
    @SuppressWarnings("unchecked")
    public E pop()  throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while(currentIdx == 0) {
                readyForRead.await();
            }
            int idx = --currentIdx;
            E result = (E) underlyingArray[idx];
            underlyingArray[idx] = null;
            readyForWrite.signal();
            return result;
        } finally {
            lock.unlock();
        }
    }
    @SuppressWarnings("unchecked")
    public E peek() {
        lock.lock();
        try {
            if (currentIdx == 0) return null;
            return (E) underlyingArray[currentIdx-1];
        }finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return currentIdx;
        } finally {
            lock.unlock();
        }
    }
    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isFull() {
        lock.lock();
        try {
            return currentIdx == capacity;
        } finally {
            lock.unlock();
        }
    }
}
