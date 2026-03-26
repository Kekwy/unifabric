package com.kekwy.unifabric.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kekwy.unifabric.common.enums.AppStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class ApplicationInfo {

    private ID id;

    /** 应用名称 */
    private String name;

    /** Git 仓库地址 */
    @JsonProperty("git_url")
    private String gitUrl;

    /** 分支 */
    private String branch = "main";

    /** 状态：idle / running / stopped / failed / deploying */
    private AppStatus status = AppStatus.APP_STATUS_IDLE;

    /** 描述 */
    private String description;

    /** 运行环境 */
    @JsonProperty("lang")
    private String lang;

    /** 最近一次构建/部署错误信息 */
    @JsonProperty("last_error")
    private String lastError;

    /** 最后部署时间 */
    @JsonProperty("last_deployed")
    private Instant lastDeployed;

    /** 创建时间 */
    @JsonProperty("created_at")
    private Instant createdAt;

}
