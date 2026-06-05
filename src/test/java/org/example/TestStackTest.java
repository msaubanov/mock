package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

class TestStackTest {

    @Test
    void pushAndPopTest() throws InterruptedException {
        TestStack<Integer> stack = new TestStack<>(3);
        for (int i = 0; i<3;i++) stack.push(i);
        for(int i = 2; i>=0;i--) Assertions.assertEquals(i,stack.pop());
        Assertions.assertNull(stack.peek());
        Assertions.assertThrows(IllegalArgumentException.class,()->stack.push(null));
    }

    //блокируется на полном стеке
    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void pushAndBlockOnFull() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        TestStack<Integer> stack = new TestStack<>(3);
        for (int i = 0; i<3;i++) stack.push(i);
        Thread th = Thread.ofPlatform().start(() -> {
            try {
                stack.push(4);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        await().atMost(2,TimeUnit.SECONDS).until(() -> Thread.State.WAITING==th.getState());
        stack.pop();
        latch.await();
        th.join();
        Assertions.assertEquals(Thread.State.TERMINATED,th.getState());
    }
    //pop блокируется на пустом стеке
    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void popAndBlockOnEmpty() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        TestStack<Integer> stack = new TestStack<>(1);
        Thread th = Thread.ofPlatform().start(() -> {
            try {
                stack.pop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        await().atMost(2,TimeUnit.SECONDS).until(() -> Thread.State.WAITING==th.getState());
        stack.push(1);
        latch.await();
        th.join();
        Assertions.assertEquals(Thread.State.TERMINATED,th.getState());
    }
    //Interrupt прерывает blocked push.
    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void blockedPushInterupted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exception = new AtomicReference<>();
        TestStack<Integer> stack = new TestStack<>(3);
        for (int i = 0; i<3;i++) stack.push(i);
        Thread th = Thread.ofPlatform().start(() -> {
            try {
                stack.push(4);
            } catch (InterruptedException e) {
                exception.set(e);
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        await().atMost(2,TimeUnit.SECONDS).until(() -> Thread.State.WAITING==th.getState());
        th.interrupt();
        latch.await();
        Assertions.assertInstanceOf(InterruptedException.class,exception.get());
        th.join();
        Assertions.assertEquals(Thread.State.TERMINATED,th.getState());
    }


    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void multipleProducerAndMultipleConsumer_WitoutDuplicates() throws InterruptedException {
        int producerCnt = 5;
        int consumerCnt = 5;
        int numberOfTask = 100;
        final int totalItems = producerCnt * numberOfTask;
        ConcurrentHashMap<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(producerCnt+consumerCnt);
        CountDownLatch startSignal = new CountDownLatch(1);
        TestStack<Integer> stack = new TestStack<>(5);//так вероятнее косяк
        try (ExecutorService producerThreadPool = Executors.newFixedThreadPool(producerCnt);
             ExecutorService consumerThreadPool = Executors.newFixedThreadPool(producerCnt)) {
            for (int i = 0; i < producerCnt; i++) {
                final int producerNum = i;
                producerThreadPool.execute(
                        () -> {
                            try {
                                startSignal.await();//ставлю в ожидание все продюсеры
                                for(int j = 0; j < numberOfTask; j++) {
                                    final Integer val = (producerNum*100)+j;
                                    stack.push(val);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                latch.countDown();
                            }
                        }
                );
            }
            final int itemsPerConsumer = totalItems / consumerCnt;
            for (int i = 0; i < consumerCnt; i++) {
                consumerThreadPool.execute(() -> {
                    try {
                        startSignal.await();
                        for (int j = 0; j < itemsPerConsumer; j++) {
                            counts.computeIfAbsent(stack.pop(), _ -> new AtomicInteger(0)).incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();//надо а зачем ????
                    } finally {
                        latch.countDown();
                    }
                });
            }
            startSignal.countDown();//по идеи все должны продюсить и консюмить в одно время
            boolean completed = latch.await(8, TimeUnit.SECONDS);//жду что бы все закрылось за 8 сек
            Assertions.assertTrue(completed);//не все треды завершилисьp

            Assertions.assertEquals(totalItems,counts.size());//
            for(Map.Entry<Integer, AtomicInteger> s : counts.entrySet()) {//проверяю на дубли
                Assertions.assertEquals(1, s.getValue().get());
            }
        }
    }
}