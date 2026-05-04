package com.aiwriter.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RequestLogAspect {

    private static final Logger log = LoggerFactory.getLogger("access");

    @Around("execution(* com.aiwriter.controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String method = "?";
        String uri = "?";
        String remoteIp = "?";

        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            method = req.getMethod();
            uri = req.getRequestURI();
            String forwarded = req.getHeader("X-Forwarded-For");
            remoteIp = forwarded != null ? forwarded : req.getRemoteAddr();
        }

        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} {} {} → {}.{}() {}ms", remoteIp, method, uri, className, methodName, elapsed);
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("{} {} {} → {}.{}() {}ms FAIL: {}", remoteIp, method, uri, className, methodName, elapsed, t.getMessage());
            throw t;
        }
    }
}
