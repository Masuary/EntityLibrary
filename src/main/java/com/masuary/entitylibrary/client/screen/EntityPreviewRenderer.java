package com.masuary.entitylibrary.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;

public final class EntityPreviewRenderer {
    private EntityPreviewRenderer() {}

    public static void render(int x, int y, int scale, float yaw, float pitch, LivingEntity entity) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.translate((double) x, (double) y, 1050.0D);
        modelViewStack.scale(1.0F, 1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();

        var poseStack = new PoseStack();
        poseStack.translate(0.0D, 0.0D, 1000.0D);
        poseStack.scale((float) scale, (float) scale, (float) scale);

        Quaternion rotZ = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion rotX = Vector3f.XP.rotationDegrees(pitch * 20.0F);
        rotZ.mul(rotX);
        poseStack.mulPose(rotZ);

        float prevBodyRot = entity.yBodyRot;
        float prevYRot = entity.getYRot();
        float prevXRot = entity.getXRot();
        float prevHeadRot = entity.yHeadRot;
        float prevHeadRotO = entity.yHeadRotO;

        entity.yBodyRot = 180.0F + yaw * 20.0F;
        entity.setYRot(180.0F + yaw * 40.0F);
        entity.setXRot(-pitch * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        Lighting.setupForEntityInInventory();

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternion cam = Vector3f.XP.rotationDegrees(pitch * 20.0F);
        cam.conj();
        dispatcher.overrideCameraOrientation(cam);
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.enableDepthTest();
        dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, buffers, 0x00F000F0);
        buffers.endBatch();
        dispatcher.setRenderShadow(true);

        entity.yBodyRot = prevBodyRot;
        entity.setYRot(prevYRot);
        entity.setXRot(prevXRot);
        entity.yHeadRot = prevHeadRot;
        entity.yHeadRotO = prevHeadRotO;

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();

        Lighting.setupFor3DItems();
    }
}
