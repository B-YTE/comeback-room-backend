package com.comebackroom.global.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

// 비동기 작업에서도 requestId가 로그에 찍히도록 MDC를 작업 스레드로 복사합니다
// 비동기용 스레드풀을 만들 때 직접 추가해 주어야 적용됩니다
// executor.setTaskDecorator(mdcTaskDecorator);
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (parentContext != null) {
                    MDC.setContextMap(parentContext);
                } else {
                    MDC.clear();
                }
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
