package com.example.supermarket.exception;

import com.example.supermarket.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * 英文异常提示 → 中文。
     *
     * 背景（2026-11-07）：service 层散落着 50+ 处英文 message（"Category not found"、
     * "Only paid orders can be shipped" 等），它们会**原样弹到后台/前台的错误弹窗里**，
     * 用户已经多次反馈「提示词得是中文，不要英文」。
     * 逐个改 17 个 service 容易漏、也难维护，所以在这里统一翻译一层：
     * 未命中的纯英文一律兜成中文（同时把原文记进日志，便于排查）。
     */
    private static final Map<String, String> MESSAGE_ZH = buildMessageZh();

    private static Map<String, String> buildMessageZh() {
        Map<String, String> m = new HashMap<>();
        // —— 数据不存在 ——
        m.put("Order not found", "订单不存在或已被删除");
        m.put("Product not found", "商品不存在或已被删除");
        m.put("User not found", "用户不存在");
        m.put("Return request not found", "售后申请不存在");
        m.put("Coupon not found", "优惠券不存在");
        m.put("Category not found", "分类不存在或已被删除");
        m.put("Parent category not found", "上级分类不存在");
        m.put("Cart item not found", "购物车条目不存在");
        m.put("Selected cart item not found", "选中的购物车条目不存在");
        m.put("Address not found", "收货地址不存在");
        m.put("Store not found", "门店不存在");
        m.put("Review not found", "评价不存在");
        m.put("Favorite not found", "收藏记录不存在");
        m.put("Activity not found", "活动不存在");
        // —— 订单状态 ——
        m.put("Only pending payment or paid orders can be canceled", "只有待付款或已付款的订单可以取消");
        m.put("Only pending payment orders can be paid", "只有待付款的订单可以支付");
        m.put("Only paid orders can be shipped", "只有已付款的订单可以发货");
        m.put("Only paid orders can be marked ready", "只有已付款的订单可以标记备货完成");
        m.put("Only shipped orders can be confirmed", "只有已发货的订单可以确认收货");
        m.put("Only shipped orders can be completed", "只有已发货的订单可以完成");
        m.put("Only completed orders can be reviewed", "只有已完成的订单可以评价");
        m.put("Order has no items to review", "该订单没有可评价的商品");
        m.put("Order item does not belong to this order", "该订单项不属于此订单");
        // —— 库存 / 余额 ——
        m.put("Failed to return stock", "库存回补失败，请重试");
        m.put("Failed to adjust stock", "库存调整失败，请重试");
        m.put("Stock cannot be negative", "库存不能为负数");
        m.put("Insufficient balance", "余额不足");
        // —— 退款 / 售后 ——
        m.put("Refund request is already under review", "该退款申请正在审核中，请勿重复提交");
        m.put("Order has no pending refund request", "该订单没有待处理的退款申请");
        m.put("Invalid refund status", "退款状态不合法");
        // —— 参数校验 ——
        m.put("SKU already exists", "商品编号已存在");
        m.put("Phone already exists", "手机号已被注册");
        m.put("Email already exists", "邮箱已被使用");
        m.put("Role is required", "请选择用户角色");
        m.put("Required field is blank", "必填项不能为空");
        m.put("Parent id is required", "请选择上级分类");
        m.put("Category name is required", "请填写分类名称");
        m.put("Category name already exists under the same parent", "同一上级下已有同名分类");
        m.put("Category cannot be its own parent", "分类不能把自己设为上级");
        m.put("Invalid user status", "用户状态不合法");
        m.put("Invalid user role", "用户角色不合法");
        m.put("Invalid product status", "商品状态不合法");
        m.put("Invalid order status", "订单状态不合法");
        m.put("Invalid category status", "分类状态不合法");
        m.put("Invalid activity status", "活动状态不合法");
        m.put("Invalid biz type", "业务类型不合法");
        m.put("Recharge amount must be greater than 0", "充值金额必须大于 0");
        m.put("Quantity or selected is required", "缺少数量或勾选状态");
        m.put("Change quantity cannot be zero", "修改后的数量不能为 0");
        // —— 管理员自身操作 ——
        m.put("Current admin cannot remove its own admin role", "不能取消自己的管理员权限");
        m.put("Current admin cannot disable itself", "不能禁用当前登录的管理员账号");
        m.put("Current admin cannot delete itself", "不能删除当前登录的管理员账号");
        return Map.copyOf(m);
    }

    /** 把英文异常提示翻成中文；本来就是中文的原样返回 */
    private static String toZh(String message) {
        if (message == null || message.isBlank()) return "操作失败，请稍后重试";
        String m = message.trim();
        String hit = MESSAGE_ZH.get(m);
        if (hit != null) return hit;
        boolean asciiOnly = m.chars().allMatch(c -> c < 128);
        if (!asciiOnly) return m; // 业务代码自己写的中文提示
        if (m.endsWith("not found")) return "数据不存在或已被删除";
        log.warn("未翻译的英文错误提示（已对用户隐藏）：{}", m);
        return "操作失败，请稍后重试";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(404, toZh(ex.getMessage())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = ex.getCode() == 401 ? HttpStatus.UNAUTHORIZED
                : ex.getCode() == 409 ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), toZh(ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, toZh(ex.getMessage())));
    }

    /**
     * 请求参数校验失败（@RequestParam / @PathVariable 上的 @Min、@Max、@NotBlank 等）。
     *
     * <p>⚠️ 原生 message 长这样：{@code listOrders.page: 最小不能小于1} —— 把**后端方法名和
     * 参数名**直接暴露到用户界面上（2026-11-08 用户在订单页截到这条 toast，要求"不要出现这种提示"）。
     * 这类信息只有开发者看得懂，所以详情只进日志，界面统一给一句人话。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations() == null ? "" : ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(java.util.stream.Collectors.joining("；"));
        log.warn("请求参数校验失败：{}", detail);
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求参数不合法，请刷新页面后重试"));
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
                .orElse("填写内容不合法，请检查后重试");
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
                .body(ApiResponse.fail(500, "服务器繁忙，请稍后重试"));
    }
}
