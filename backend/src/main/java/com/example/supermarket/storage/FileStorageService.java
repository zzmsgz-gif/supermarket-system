package com.example.supermarket.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * 文件存储抽象。业务方只依赖此接口。
 *
 * <p>目前<b>只有本地磁盘一种实现</b>：阿里云 OSS 已按用户要求彻底移除（2026-11-20），
 * 依赖、OssFileStorageService、配置块均已删除，不存在切换到云存储的路径。
 */
public interface FileStorageService {

    /**
     * 存储一个上传文件，返回可公开访问的 URL。
     *
     * @param bizType 业务类型：product / category / avatar / review / misc
     * @param file    上传的文件
     * @return 可访问的 URL（固定为 /api/uploads/...，文件落在后端本地磁盘）
     */
    String store(String bizType, MultipartFile file) throws IOException;
}
