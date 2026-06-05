package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

class TestQueueTest {
    //-------single thread tests---
    @Test
    void initError() {
        Assertions.assertThrowsExactly(IllegalArgumentException.class,() -> new TestQueue<>(0),"Invalid size");
    }
    @Test
    void initSuccess() {
        Assertions.assertDoesNotThrow(() -> new TestQueue<>(1));
    }
    @Test
    void putNull () throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(2);
        Assertions.assertThrows(IllegalArgumentException.class,() -> queue.put(null));
    }
    @Test
    void lastPutFirstTake() throws InterruptedException {
        Integer element = 31;
        TestQueue<Integer> queue = new TestQueue<>(2);
        queue.put(element);
        Assertions.assertEquals(element,queue.take());
    }
    @Test
    void putAndTakeWithMany() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(3);
        for(Integer e : List.of(1, 2, 3)) queue.put(e);
        for(Integer e : List.of(1, 2, 3)) Assertions.assertEquals(e,queue.take());
    }
    @Test
    void testPeakAndSize() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(3);
        for(Integer e : List.of(1, 2, 3)) queue.put(e);
        Integer top = queue.peek();
        Assertions.assertEquals(top,queue.take());
        Assertions.assertEquals(2,queue.getSize());
    }
    @Test
    void testPeakOnNull() {
        TestQueue<Integer> queue = new TestQueue<>(3);
        Assertions.assertNull(queue.peek());
    }
    @Test
    void testEmptyNonEmpty() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(3);
        Assertions.assertEquals(0,queue.getSize());
        queue.put(1);
        Assertions.assertEquals(1,queue.getSize());
    }
    @Test
    void putTakeTwiceOnSizeOne() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(1);
        queue.put(1);
        Assertions.assertEquals(1,queue.take());
        queue.put(2);
        Assertions.assertEquals(2,queue.take());
    }
    @Test
    void testWithCycle() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(3);
        for(Integer e : List.of(1, 2, 3)) queue.put(e);
        Assertions.assertEquals(1,queue.take());
        queue.put(4);
        Assertions.assertEquals(2,queue.take());
        Assertions.assertEquals(3,queue.take());
        Assertions.assertEquals(4,queue.take());
    }
    //-------
    //test for blocking
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void putAsyncOnOverflow() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(3);
        for(Integer e : List.of(1, 2, 3)) queue.put(e);
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                queue.put(4);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        Thread.sleep(100);
        Assertions.assertEquals(Thread.State.WAITING,thread.getState());
        queue.take();
        latch.await();
        thread.join();
        Assertions.assertEquals(Thread.State.TERMINATED,thread.getState());
        for(int i = 0; i <= 1; i++) queue.take();
        Assertions.assertEquals(4,queue.take());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void takeAsyncOnNull() throws InterruptedException {
        TestQueue<Integer> queue = new TestQueue<>(1);
        queue.put(1);
        queue.take();
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                queue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        Thread.sleep(100);
        Assertions.assertEquals(Thread.State.WAITING,thread.getState());
        queue.put(1);
        latch.await();
        Assertions.assertEquals(Thread.State.TERMINATED,thread.getState());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldInterruptBlockedPutThread() throws InterruptedException {
        AtomicReference<Throwable> exception = new AtomicReference<>();
        TestQueue<Integer> queue = new TestQueue<>(1);
        queue.put(1);
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                queue.put(4);
            } catch (Throwable e) {
                exception.set(e);
            }
        });
        await().atMost(2, TimeUnit.SECONDS).until(() -> thread.getState() == Thread.State.WAITING);
        thread.interrupt();
        thread.join(2000);
        Assertions.assertFalse(thread.isAlive());
        Assertions.assertNotNull(exception.get());
        Assertions.assertEquals(1,queue.getSize());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void takeBlockedUnderEmptyAndinterapted() throws InterruptedException {
        AtomicReference<Throwable> exception = new AtomicReference<>();
        TestQueue<Integer> queue = new TestQueue<>(1);
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                queue.take();
            } catch (Throwable e) {
                exception.set(e);
            }
        });
        await().atMost(2, TimeUnit.SECONDS).until(() -> thread.getState() == Thread.State.WAITING);
        thread.interrupt();
        thread.join(2000);
        Assertions.assertFalse(thread.isAlive());
        Assertions.assertInstanceOf(InterruptedException.class,exception.get());
        Assertions.assertEquals(0,queue.getSize());
    }
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void severalProducerLockerWhenRichSizeLimits() throws InterruptedException {
        AtomicReference<Throwable> exceptions = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        TestQueue<Integer> queue = new TestQueue<>(1);
        queue.put(0);
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                queue.put(4);
            } catch (InterruptedException e) {
                exceptions.set(e);
                throw new RuntimeException(e);
            }finally {
                latch.countDown();
            }
        });
        Thread thread1 = Thread.ofPlatform().start(() -> {
            try {
                queue.put(5);
            } catch (InterruptedException e) {
                exceptions.set(e);
                throw new RuntimeException(e);
            }finally {
                latch.countDown();
            }
        });
        await().atMost(2,TimeUnit.SECONDS).until(() -> thread.getState()==Thread.State.WAITING && thread1.getState() == Thread.State.WAITING);
        Assertions.assertEquals(Thread.State.WAITING,thread.getState());
        Assertions.assertEquals(Thread.State.WAITING,thread1.getState());
        Integer first = queue.take();
        Assertions.assertEquals(0, first);
        await().atMost(2,TimeUnit.SECONDS).until(() -> {
            int terminated = 0;
            terminated+=(thread.getState()==Thread.State.TERMINATED) ? 1 :0;
            terminated+=(thread1.getState()==Thread.State.TERMINATED) ? 1 : 0;
            return terminated == 1;
        });
        Integer second = queue.take();
        Assertions.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assertions.assertEquals(Thread.State.TERMINATED, thread.getState());
        Assertions.assertEquals(Thread.State.TERMINATED, thread1.getState());
    }

    //попробую тестить инварианты
    @Test
    //@RepeatedTest(value = 100)//не хочу долго
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void producedAndCunsumeEquality() throws InterruptedException {
        int producerCnt = 10;
        int consumerCnt = 10;
        int numberOfTask = 100;
        final int totalItems = producerCnt * numberOfTask;
        Set<Integer> producer = ConcurrentHashMap.newKeySet();
        Set<Integer> consumer = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(producerCnt+consumerCnt);
        CountDownLatch startSignal = new CountDownLatch(1);
        TestQueue<Integer> queue = new TestQueue<>(50);

        try (ExecutorService producerThreadPool = Executors.newFixedThreadPool(producerCnt);
             ExecutorService consumerThreadPool = Executors.newFixedThreadPool(consumerCnt)) {

            for (int i = 0; i < producerCnt; i++) {
                final int producerNum = i;
                producerThreadPool.execute(
                        () -> {
                            try {
                                startSignal.await();//ставлю в ожидание все продюсеры
                                for(int j = 0; j < numberOfTask; j++) {
                                    final Integer val = (producerNum*1000)+j;
                                    queue.put(val);
                                    producer.add(val);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                latch.countDown();
                            }
                        }
                );
            }
            AtomicInteger consumedCount = new AtomicInteger(0);
            for (int i = 0; i < consumerCnt; i++) {
                consumerThreadPool.execute(
                        () -> {
                            try {
                                startSignal.await();//ставлю в ожидание все консумеры
                                for(int j = 0; j < numberOfTask; j++) {
                                    consumer.add(queue.take());
                                    consumedCount.incrementAndGet();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                latch.countDown();
                            }
                        }
                );
            }
            startSignal.countDown();//по идеи все должны продюсить и консюмить в одно время
            boolean completed = latch.await(8, TimeUnit.SECONDS);//жду что бы все закрылось за 8 сек
            Assertions.assertTrue(completed);//не все треды завершилисьp
            Assertions.assertEquals(producer.size(), consumer.size());//
            Assertions.assertEquals(totalItems, producer.size());
            Assertions.assertEquals(totalItems, consumer.size());
            Assertions.assertEquals(new HashSet<>(producer), new HashSet<>(consumer));
        }
    }
    //n консумеров не должны забрать один и тот же элемент
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConsumerConcurrent() throws InterruptedException {
        int consumerCnt = 10;
        int numberOfTask = 100;
        //вроде как надо сравнить консумеры вернее то что каждый собрал уникальный сет
        ConcurrentHashMap<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(consumerCnt);
        CountDownLatch startSignal = new CountDownLatch(1);
        TestQueue<Integer> queue = new TestQueue<>(1000);
        for (int i = 0; i < consumerCnt*numberOfTask; i++) {
            queue.put(i);
        }
        try (ExecutorService consumerThreadPool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < consumerCnt; i++) {
                consumerThreadPool.execute(
                        () -> {
                            try {
                                startSignal.await();//ставлю в ожидание все консумеры
                                for(int j = 0; j < numberOfTask; j++) {
                                    counts.computeIfAbsent(queue.take(),
                                            k -> new AtomicInteger(0)).incrementAndGet();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                latch.countDown();
                            }
                        }
                );
            }
            startSignal.countDown();//по идеи все должны  консюмить в одно время
            boolean completed = latch.await(8, TimeUnit.SECONDS);//жду что бы все закрылось за 8 сек
            Assertions.assertTrue(completed);// все ли треды завершились?
            for(Map.Entry<Integer, AtomicInteger> s : counts.entrySet()) {//проверяю на дубли
                Assertions.assertEquals(1, s.getValue().get());
            }
        }
    }
}