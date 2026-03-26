package com.kekwy.unifabric.common.util;

import com.kekwy.unifabric.common.model.ID;

import java.util.UUID;

public class IDUtil {

    private static String genUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static ID genAppID() {
        ID id = new ID();
        id.setValue("app." + genUUID());
        return id;
    }

    public static ID genWorkspaceID() {
        ID id = new ID();
        id.setValue("workspace." + genUUID());
        return id;
    }

    public static ID genArtifactID() {
        ID id = new ID();
        id.setValue("artifact." + genUUID());
        return id;
    }

}
