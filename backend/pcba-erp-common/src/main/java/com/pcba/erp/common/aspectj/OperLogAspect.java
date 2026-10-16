package com.pcba.erp.common.aspectj;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.utils.JsonUtils;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.event.OperLogEvent;
import com.pcba.erp.common.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志切面: AOP 记录方法调用信息, 发布 Spring Event 异步落库。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final ApplicationEventPublisher publisher;

    @Around("@annotation(operLogAnno)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLogAnno) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Exception error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            try { handleEvent(pjp, operLogAnno, result, error, System.currentTimeMillis() - start); }
            catch (Exception e) { log.warn("记录操作日志失败", e); }
        }
    }

    private void handleEvent(ProceedingJoinPoint pjp, OperLog ann, Object result, Exception ex, long cost) {
        HttpServletRequest req = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) req = attrs.getRequest();

        String methodName = pjp.getSignature().getDeclaringTypeName() + "#" + pjp.getSignature().getName();
        String requestMethod = req == null ? "" : req.getMethod();
        String url = req == null ? "" : req.getRequestURI();
        String ip = req == null ? "" : ipOf(req);
        String operName = "", deptName = "";
        LoginUser u = SecurityUtils.getLoginUserSafe();
        if (u != null) { operName = u.getUsername(); }

        String reqParams = "";
        if (ann.saveRequestData()) {
            try {
                Object[] args = pjp.getArgs();
                if (args != null) {
                    Map<String, Object> named = args.length > 0
                            ? java.util.stream.IntStream.range(0, args.length)
                            .boxed()
                            .collect(Collectors.toMap(
                                    i -> ((MethodSignature) pjp.getSignature()).getParameterNames()[i],
                                    i -> filterSensitive(args[i], ann.excludeParamNames())
                            )) : java.util.Collections.emptyMap();
                    reqParams = JsonUtils.toJson(named);
                    if (reqParams.length() > 4000) reqParams = reqParams.substring(0, 4000) + "...(truncated)";
                }
            } catch (Exception e) { /* ignore */ }
        }
        String resp = "";
        if (ann.saveResponseData() && result != null) {
            try { resp = JsonUtils.toJson(result); if (resp.length() > 2000) resp = resp.substring(0, 2000) + "..."; }
            catch (Exception e) { /* ignore */ }
        }

        OperLogEvent evt = OperLogEvent.builder()
                .title(ann.title())
                .businessType(ann.businessType())
                .method(methodName)
                .requestMethod(requestMethod)
                .operName(operName)
                .deptName(deptName)
                .operUrl(url)
                .operIp(ip)
                .operParam(reqParams)
                .jsonResult(resp)
                .status(ex == null ? 0 : 1)
                .errorMsg(ex == null ? null : (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage().substring(0, Math.min(2000, ex.getMessage().length()))))
                .operTime(LocalDateTime.now())
                .costTime(cost)
                .build();
        publisher.publishEvent(evt);
    }

    private Object filterSensitive(Object arg, String[] excludes) {
        if (arg == null) return null;
        if (arg instanceof MultipartFile f) return "[File " + f.getOriginalFilename() + "]";
        if (arg.getClass().isArray()) return Arrays.toString((Object[]) arg);
        if (excludes != null && excludes.length > 0 && arg instanceof Map map) {
            Map<Object, Object> copy = new java.util.HashMap<>(map);
            for (String k : excludes) copy.remove(k);
            return copy;
        }
        if (excludes != null && excludes.length > 0) {
            // 反射过滤字段
            try {
                Map<String, Object> m = JsonUtils.toMap(arg);
                for (String k : excludes) m.remove(k);
                return m;
            } catch (Exception e) { /* ignore */ }
        }
        return arg;
    }

    private static String ipOf(HttpServletRequest r) {
        String[] heads = {"X-Forwarded-For","X-Real-IP","Proxy-Client-IP","WL-Proxy-Client-IP"};
        for (String h : heads) {
            String v = r.getHeader(h);
            if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
                int idx = v.indexOf(',');
                return idx < 0 ? v : v.substring(0, idx).trim();
            }
        }
        return r.getRemoteAddr();
    }
}
