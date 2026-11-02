package com.example.supermarket.exception;

import com.example.supermarket.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(404, ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = ex.getCode() == 401 ? HttpStatus.UNAUTHORIZED
                : ex.getCode() == 409 ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // 2026-10-12：原文案是英文 "Data conflict"，用户领券撞唯一键时弹出来完全看不懂。
        // 真正的原因（如每日券跨天再领）已在各 Service 层用中文拦住；走到这里说明是
        // 并发/重复提交撞了数据库约束，提示刷新重试即可。
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(409, "操作冲突，可能重复提交了，请刷新后重试"));
    }

    // 并发锁冲突：MySQL 1213 死锁 / 1205 锁等待超时（CannotAcquireLockException、
    // DeadlockLoserDataAccessException 都继承 PessimisticLockingFailureException）。
    // 这类异常发生时事务已被数据库整体回滚，数据是干净的，用户重试一次即可 ——
    // 绝不能兜成 500：那样用户看到「服务器内部错误」，会以为下单失败是系统故障，
    // 也分不清「是我手慢了还是系统崩了」（2026-11-02 并发抢购实测出现，顺带收成 409）。
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockConflict(PessimisticLockingFailureException ex) {
        log.warn("并发锁冲突（事务已回滚，可安全重试）：{}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(409, "当前操作的人有点多，请稍后重试"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, message));
    }

    // 文件超过 multipart 上限（Hard 上限，兜底用）：返回友好提示，绝不兜成 500 Server error
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(413, "图片大小不能超过 10MB，请压缩后再上传"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String supported = ex.getSupportedHttpMethods() == null ? "" : ex.getSupportedHttpMethods().toString();
        String msg = "请求方法不被支持：" + ex.getMethod() + "，本接口支持的 HTTP 方法为 " + supported;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail(405, msg));
    }

    // 缺少必填请求参数（如 @RequestParam 未传）：属于调用方错误，应返回 400 而非 500 Server error，
    // 避免把这类可预期的前端/调用方失误暴露成「服务器内部错误」（2026-10-18 优惠券 usable 接口 500 复盘）。
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String msg = "缺少必填参数：" + ex.getParameterName();
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg));
    }

    // 畸形 / 非 JSON 请求体（如调用方发了非 JSON body、或 body 字段与 DTO 不匹配）：
    // 属于调用方错误，必须返回 400 + 干净的错误结构，而不是被兜底成 500 Server error。
    // 2026-10-28 复核：另一 AI 测出「鉴权接口全线 500」，很可能就是发了 malformed body
    // 触发这里被兜底——修好后这类请求会拿到明确的 400 而非伪装成服务器故障。
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求体格式错误，请检查 JSON 是否合法"));
    }

    // 路径 / 参数类型不匹配（如 /products/abc，而 id 是 Long）：同样属于调用方错误，
    // 当前会被兜底成 500，这里显式收成 400。
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingPathVariableException.class})
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求参数类型错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "Server error"));
    }
}
