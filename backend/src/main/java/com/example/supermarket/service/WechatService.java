package com.example.supermarket.service;

import com.example.supermarket.exception.BusinessException;
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

    private final String appId;
    private final String secret;
    private final RestTemplate restTemplate = new RestTemplate();

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
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) (Map<?, ?>) restTemplate.getForObject(url, Map.class);
            resp = raw;
        } catch (Exception e) {
            throw new BusinessException(500, "调用微信登录接口失败：" + e.getMessage());
        }
        if (resp == null) {
            throw new BusinessException(500, "微信登录接口无响应");
        }
        Object errcode = resp.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            throw new BusinessException(401, "微信登录失败：errcode=" + errcode + "，errmsg=" + resp.get("errmsg"));
        }
        Object openid = resp.get("openid");
        if (openid == null || String.valueOf(openid).isBlank()) {
            throw new BusinessException(401, "微信登录未返回 openid");
        }
        return String.valueOf(openid);
    }
}
