package com.pvub.disruptordemo;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.BackpressureOverflow;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Producer which generates multiple messages
 * @author Udai
 */
public class RxEventProducer {
    private final JsonObject m_config;
    private final PublishSubject<MessageEvent> m_subject;
    private ExecutorService m_executor = null;
    private Scheduler       m_scheduler;
    private final Logger    m_logger;
    private Subscription    m_subscription = null;
    private AtomicLong      m_counter = new AtomicLong(0);
    private JsonObject      m_stats = new JsonObject();

    public RxEventProducer(JsonObject config, final PublishSubject<MessageEvent> subject) {
        m_config = config;
        m_subject = subject;
        m_logger = LoggerFactory.getLogger("RXPRODUCER");
        Integer worker_pool_size = config.getInteger("worker-pool-size", 1);
        m_executor = Executors.newFixedThreadPool(worker_pool_size);
        m_scheduler = Schedulers.from(m_executor);
    }
    
    public void stop() {
        m_logger.info("Stopping disruptor");
        if (m_subscription != null) {
            m_subscription.unsubscribe();
            m_subscription = null;
        }
        try {
            m_executor.shutdown();
            m_executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            m_logger.error("Error stopping Producer's executor service", ex);
        }
    }
    
    public void start() {
        m_logger.info("Starting producer");
        final int events_per_microsecond = m_config.getInteger("events-per-microsecond", 100);
        m_subscription = Observable.interval(1000/events_per_microsecond, TimeUnit.NANOSECONDS)
                                .onBackpressureBuffer(events_per_microsecond * 100000000
                                                     , () -> { 
                                                         m_logger.info("Producer backpressure"); 
                                                     }
                                                     , BackpressureOverflow.ON_OVERFLOW_DROP_LATEST)
                                .subscribeOn(Schedulers.io())
                                .observeOn(m_scheduler)
                                .subscribe(delay -> {
                                            final MessageEvent mEvent = new MessageEvent();
                                            mEvent.setValue(100);
                                            m_subject.onNext(mEvent);
                                            m_counter.incrementAndGet();
                                        }, 
                                        error -> {
                                            m_logger.error("Producer error", error);
                                        }, 
                                        () -> {
                                            m_logger.info("Ending producer timer");
                                        });
    }

    public String getStats() {
        m_stats.put("count", m_counter.getAndSet(0));
        return Json.encode(m_stats);
    }
    public long getCount() {
        return m_counter.getAndSet(0);
    }
}