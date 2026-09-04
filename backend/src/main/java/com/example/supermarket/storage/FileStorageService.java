package com.example.supermarket.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * 文件存储抽象。业务方只依赖此接口，具体实现可在本地磁盘 / 阿里云 OSS 之间切换，
 * 通过 application.yml 的 storage.type 选择，代码零改动。
 */
public interface FileStorageService {

    /**
     * 存储一个上传文件，返回可公开访问的 URL。
     *
     * @param bizType 业务类型：product / category / avatar / review / misc
     * @param file    上传的文件
     * @return 可访问的 URL（本地模式为 /api/uploads/...，OSS 模式为对象存储公网地址）
     */
    String store(String bizType, MultipartFile file) throws IOException;
}
