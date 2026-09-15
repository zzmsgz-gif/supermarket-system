package com.example.supermarket.service;

import com.example.supermarket.dto.LegalDocRequest;
import com.example.supermarket.dto.LegalDocResponse;
import com.example.supermarket.entity.LegalDoc;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.LegalDocRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协议 / 隐私政策等法律文本：前台按 key 读取，后台可编辑。
 *
 * <p>首次启动会自动写入一版**模板正文**（见 {@link #ensureDefaults()}），
 * 正文里的主体信息全部是【】占位符，上线前必须在后台替换并经法务确认。
 */
@Service
public class LegalDocService {

    private static final Logger log = LoggerFactory.getLogger(LegalDocService.class);

    private static final byte ENABLED = 1;

    private final LegalDocRepository legalDocRepository;

    public LegalDocService(LegalDocRepository legalDocRepository) {
        this.legalDocRepository = legalDocRepository;
    }

    @Transactional(readOnly = true)
    public LegalDocResponse getPublic(String docKey) {
        String key = normalizeKey(docKey);
        return legalDocRepository.findByDocKeyAndEnabled(key, ENABLED)
                .map(LegalDocResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("该文档不存在或未启用"));
    }

    @Transactional(readOnly = true)
    public List<LegalDocResponse> listPublic() {
        return legalDocRepository.findByEnabledOrderBySortNoAscIdAsc(ENABLED).stream()
                .map(LegalDocResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LegalDocResponse> listAdmin() {
        return legalDocRepository.findAllByOrderBySortNoAscIdAsc().stream()
                .map(LegalDocResponse::from)
                .toList();
    }

    @Transactional
    public LegalDocResponse update(String docKey, LegalDocRequest request) {
        String key = normalizeKey(docKey);
        LegalDoc doc = legalDocRepository.findByDocKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("该文档不存在"));
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            doc.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            doc.setContent(request.getContent());
        }
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            doc.setVersion(request.getVersion().trim());
        }
        if (request.getEnabled() != null) {
            doc.setEnabled((byte) (int) request.getEnabled());
        }
        return LegalDocResponse.from(legalDocRepository.saveAndFlush(doc));
    }

    /**
     * 保证预置文档存在（幂等）：已存在则**不覆盖**后台改过的正文。
     * 与 DataSeeder 同一思路 —— 只在缺行时补种子，避免重启把运营编辑的内容冲掉。
     */
    @Transactional
    public void ensureDefaults() {
        int created = 0;
        for (DefaultDoc template : DEFAULTS) {
            if (legalDocRepository.findByDocKey(template.key()).isPresent()) {
                continue;
            }
            LegalDoc doc = new LegalDoc();
            doc.setDocKey(template.key());
            doc.setTitle(template.title());
            doc.setContent(template.content());
            doc.setVersion("v1.0");
            doc.setEnabled(ENABLED);
            doc.setSortNo(template.sortNo());
            legalDocRepository.save(doc);
            created++;
        }
        if (created > 0) {
            log.info("[legal-doc] 已写入 {} 份协议模板（正文含【】占位符，上线前请在后台替换）", created);
        }
    }

    private String normalizeKey(String docKey) {
        if (docKey == null || docKey.isBlank()) {
            throw new ResourceNotFoundException("该文档不存在");
        }
        return docKey.trim().toUpperCase(Locale.ROOT);
    }

    private record DefaultDoc(String key, String title, int sortNo, String content) {
    }

    /** 模板正文：主体信息一律用【】占位，明确提示需替换 */
    private static final List<DefaultDoc> DEFAULTS = List.of(
            new DefaultDoc(LegalDoc.KEY_TERMS, "用户协议", 10, """
                    【模板提示：本正文由系统预置，上线前请把全部【】占位符替换为真实信息，
                    并经法务/合规确认后，在后台「内容管理 → 协议与隐私」保存。生效日期：____年__月__日】

                    欢迎使用【平台名称】（以下简称"本平台"）。本平台由【公司全称】（统一社会信用代码：【统一社会信用代码】）运营。
                    请您在注册、登录或使用本平台服务前，认真阅读并充分理解本协议全部内容，特别是以粗体标注的免责条款、责任限制条款。
                    您勾选"我已阅读并同意《用户协议》"或实际使用本平台服务，即表示您已接受本协议全部条款。

                    一、协议范围
                    1.1 本协议是您与本平台之间就商品浏览、下单、支付、配送、售后等事项所订立的约定。
                    1.2 本平台可能就特定服务另行发布规则（如优惠券规则、积分规则、限时秒杀规则），该等规则与本协议不一致的，以特别规则为准。

                    二、账号注册与安全
                    2.1 您应使用真实、准确、完整的手机号等信息完成注册，并在信息变更时及时更新。
                    2.2 账号及密码由您自行保管，您应对以该账号进行的一切操作负责。如发现账号被非法使用，请立即联系我们。
                    2.3 您可随时在【个人中心】申请注销账号。注销后，我们将按《隐私政策》处理您的个人信息，账号下的优惠券、积分等权益将一并清空且不可恢复。

                    三、商品、价格与库存
                    3.1 本平台展示的商品信息（名称、规格、产地、保质期等）由我们或供应商提供，我们将尽合理努力保证其准确。
                    3.2 商品价格以下单时页面展示的价格为准。页面标注的划线价（如有）为该商品在本平台的日常销售价或供应商指导价，不作为原价的法定含义；具体的优惠幅度以结算页实际计算为准。
                    3.3 **受生鲜商品保质期与库存波动影响，商品可能出现缺货。若您下单后发生缺货，我们将及时通知您并全额退款。**
                    3.4 限时秒杀商品具有名额限制与时间限制，名额抢完或活动结束后价格自动恢复，以系统记录为准。

                    四、下单、支付与配送
                    4.1 您可选择"送货上门"或"门店自提"。选择自提的，请凭订单中的自提码在营业时间内到所选门店取货；选择送货上门的，请填写真实有效的收货信息。
                    4.2 订单提交后请在页面提示的时间内完成支付；逾期未支付的，订单将自动关闭并释放占用的库存与优惠名额。
                    4.3 配送时段为预计送达时间，**受天气、交通等因素影响可能延迟，我们不因此承担违约责任，但会及时告知进展。**

                    五、优惠券、积分与会员权益
                    5.1 优惠券、积分等权益的使用条件、有效期以券面及活动页面说明为准，除法律法规另有规定外不可折现、不可转让。
                    5.2 会员等级折扣、积分抵扣的规则可能调整，调整后对新产生的订单生效，不影响已完成的订单。
                    5.3 **以不正当手段（包括但不限于虚假注册、恶意刷单、利用系统漏洞）获取的优惠券、积分及优惠，我们有权撤销并追究责任。**

                    六、退换货与售后
                    6.1 生鲜类商品因性质特殊，签收后请在合理时间内检查；**如遇变质、破损、缺斤少两等问题，请在签收后 24 小时内联系我们并提供照片**，我们将按"坏果包赔"承诺处理。
                    6.2 非生鲜类商品在不影响二次销售的前提下，可在签收后 7 日内申请退换。
                    6.3 退款将原路退回您的账户余额或支付渠道，具体到账时间以支付机构处理时效为准。

                    七、用户行为规范
                    7.1 您承诺不利用本平台从事违法违规活动，不发布违法、侵权、虚假信息，不以技术手段干扰平台正常运行。
                    7.2 如您违反上述约定，我们有权视情况采取警告、限制功能、暂停或终止账号等措施，并保留追究法律责任的权利。

                    八、知识产权
                    8.1 本平台的商标、标识、页面设计、程序代码等知识产权归我们或相应权利人所有。
                    8.2 未经书面许可，您不得复制、传播、修改或用于任何商业用途。

                    九、免责与责任限制
                    9.1 **因不可抗力、基础电信运营商故障、第三方支付渠道故障等非我们原因导致服务中断或数据延迟的，我们不承担责任。**
                    9.2 在法律允许的最大范围内，我们对您的赔偿责任总额不超过您就该笔订单实际支付的金额。
                    9.3 本条不排除依法不得排除或限制的责任。

                    十、协议变更与终止
                    10.1 我们可能根据法律法规变化或业务需要修订本协议，修订后将在本页面公示并标注生效日期；**如您不同意修订内容，请停止使用并注销账号，继续使用即视为接受。**
                    10.2 您可随时停止使用本平台并注销账号，本协议自账号注销之日起终止。

                    十一、法律适用与争议解决
                    11.1 本协议的订立、效力、履行与解释均适用中华人民共和国法律（不含中国香港、中国澳门、中国台湾地区法律）。
                    11.2 因本协议产生争议的，双方应友好协商解决；协商不成的，任何一方可向【公司注册地】有管辖权的人民法院提起诉讼。

                    十二、联系我们
                    客服电话：【客服电话】
                    联系邮箱：【联系邮箱】
                    注册地址：【注册地址】
                    """),
            new DefaultDoc(LegalDoc.KEY_PRIVACY, "隐私政策", 20, """
                    【模板提示：本正文由系统预置，上线前请把全部【】占位符替换为真实信息，
                    并经法务/合规确认后，在后台「内容管理 → 协议与隐私」保存。生效日期：____年__月__日】

                    【公司全称】（以下简称"我们"）深知个人信息对您的重要性。本政策说明我们在您使用本平台时如何收集、使用、存储、共享和保护您的个人信息，以及您享有的权利。
                    请在使用本平台前仔细阅读本政策，**特别关注以粗体标注的敏感个人信息处理条款。**

                    一、我们收集哪些信息
                    1.1 账号信息：您注册时提供的手机号、用户名、密码（加密存储，我们无法查看您的明文密码）。
                    1.2 个人资料：您自愿填写的昵称、头像、电子邮箱。
                    1.3 收货与联系信息：**收货人姓名、联系电话、收货地址**。选择门店自提时，我们仅使用您的账号手机号作为取货核验依据。
                    1.4 交易信息：您购买的商品、金额、下单时间、支付状态、发票信息（如有）。
                    1.5 使用信息：浏览记录、搜索关键词、设备型号、操作系统、IP 地址、访问时间等日志信息。
                    1.6 我们**不会收集**您的身份证号、银行卡号、生物识别信息等与购物无关的敏感信息。

                    二、我们如何使用信息
                    2.1 完成下单、支付、配送、自提核验与售后处理。
                    2.2 计算会员等级、发放优惠券与积分、实现商品推荐（基于您的浏览与购买记录）。
                    2.3 保障账号与交易安全，识别并防范欺诈、刷单等风险。
                    2.4 经您单独同意的其他用途。**如需将信息用于本政策未载明的其他目的，我们将再次征求您的同意。**

                    三、Cookie 与同类技术
                    3.1 我们使用本地存储（localStorage）保存您的登录凭证，以便您免重复登录。
                    3.2 您可通过浏览器设置清除本地存储，但清除后需要重新登录。

                    四、我们如何共享、转让、公开披露信息
                    4.1 我们不会向任何第三方出售您的个人信息。
                    4.2 为实现订单履约，我们可能将**收货人姓名、电话、地址**提供给配送服务商；将订单金额与支付单号提供给支付机构。
                    4.3 为完成短信通知，我们可能将**手机号**提供给短信服务商。
                    4.4 仅在法律法规要求、司法机关或行政机关依法定程序要求时，我们才会披露相关信息。
                    4.5 除合并、分立等情形外，我们不会将您的个人信息转让给任何第三方；如发生转让，将告知您并要求承接方继续受本政策约束。

                    五、我们如何保护信息
                    5.1 我们采用加密传输、密码哈希存储、访问权限最小化、操作审计等措施保护您的信息。
                    5.2 **请注意：互联网环境并非绝对安全，请您妥善保管账号密码；如发生安全事件，我们将按法律要求及时告知您。**

                    六、您的权利
                    6.1 访问与更正：您可在【个人中心】查看并修改您的昵称、头像、手机号、邮箱与收货地址。
                    6.2 删除：在法律法规允许的范围内，您可请求删除您的个人信息。
                    6.3 注销账号：您可申请注销账号。注销后我们将停止提供服务，并对您的个人信息进行删除或匿名化处理；**但依据《电子商务法》等规定，商品和服务信息、交易信息的保存时间自交易完成之日起不少于三年，该部分信息将在法定保存期届满后删除。**
                    6.4 撤回同意：您可撤回对商品推荐等非必要功能的授权。
                    6.5 上述请求可通过【联系邮箱】提出，我们将在 15 个工作日内答复。

                    七、信息存储期限与地域
                    7.1 您的个人信息存储于中华人民共和国境内，不向境外传输。
                    7.2 存储期限为实现本政策所述目的所必需的最短时间；超出期限后我们将删除或匿名化处理，法律法规另有规定的除外。

                    八、未成年人保护
                    8.1 本平台主要面向成年人。**如您为未满 14 周岁的儿童，请在监护人陪同下阅读本政策，并在监护人同意后使用本平台。**
                    8.2 我们不会主动收集儿童个人信息；如发现误收集，将立即删除。

                    九、本政策的更新
                    9.1 本政策更新后，我们将在本页面公示并标注生效日期。**涉及处理目的、信息种类、共享对象等重大变更的，我们将以显著方式提示并重新征求您的同意。**

                    十、如何联系我们
                    如您对本政策或个人信息处理有任何疑问、意见或投诉，可通过以下方式联系我们：
                    客服电话：【客服电话】
                    联系邮箱：【联系邮箱】
                    注册地址：【注册地址】
                    """));
}
