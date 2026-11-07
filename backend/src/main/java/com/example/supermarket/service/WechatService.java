package com.example.supermarket.service;

import com.example.supermarket.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * 微信小程序能力封装。
 *
 * <p>当前仅实现「code2Session」（微信登录换 openid）。微信支付本期不做（无商户号），
 * 小程序下单仍走现有 BALANCE 钱包渠道，与 PC/H5 一致。
 *
 * <p>⚠️ appid / secret 一律通过 {@code @Value} 从配置注入，代码里**不写死任何真实值**
 * （仓库公开，写死等于泄密）。本地值放被 {@code .gitignore} 排除的 application.yml。
 */
@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    private final String appId;
    private final String secret;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatService(
            @Value("${wechat.mp.appid:}") String appId,
            @Value("${wechat.mp.secret:}") String secret
    ) {
        this.appId = appId;
        this.secret = secret;
    }

    /**
     * 用 wx.login 拿到的临时 code 向微信换取 openid。
     *
     * @return 微信用户的 openid
     * @throws BusinessException 配置缺失 / 微信返回错误 / 网络异常
     */
    public String code2Session(String code) {
        if (appId.isBlank() || secret.isBlank()) {
            throw new BusinessException(500, "微信登录未配置（缺少 wechat.mp.appid / wechat.mp.secret）");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appId
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        Map<String, Object> resp;
        try {
            // ⚠️ 必须按 **String** 接收再自己解析 JSON：
            // 微信 jscode2session 在出错时（invalid code / invalid appsecret）返回的是
            // `Content-Type: text/plain` 而不是 application/json，直接用 Map 接收会抛
            // "no suitable HttpMessageConverter found for response type [Map] and content type [text/plain]"
            // —— 于是微信的真实错误被吃掉，用户只看到一串英文技术信息（2026-11-07 线上实测踩到）。
            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(500, "微信登录接口无响应");
            }
            log.debug("jscode2session 原始返回：{}", raw);
            resp = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用微信 jscode2session 失败", e);
            throw new BusinessException(500, "微信登录服务暂时不可用，请稍后重试");
        }
        if (resp == null) {
            throw new BusinessException(500, "微信登录接口无响应");
        }
        Object errcode = resp.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            // 把微信原始错误记进日志便于排查，但**别把 errcode/errmsg 直接甩给用户**（用户看不懂）
            log.warn("微信登录返回错误：errcode={}, errmsg={}", errcode, resp.get("errmsg"));
            String errCode = String.valueOf(errcode);
            String friendly = switch (errCode) {
                case "40029", "40163" -> "登录凭证已失效，请重新点击登录";
                case "40125", "41004" -> "小程序登录配置有误，请联系管理员";
                case "45011" -> "登录太频繁，请稍后再试";
                case "-1" -> "微信服务繁忙，请稍后重试";
                default -> "微信登录失败，请稍后重试";
            };
            throw new BusinessException(401, friendly);
        }
        Object openid = resp.get("openid");
        if (openid == null || String.valueOf(openid).isBlank()) {
            throw new BusinessException(401, "微信登录未返回 openid");
        }
        return String.valueOf(openid);
    }
}
