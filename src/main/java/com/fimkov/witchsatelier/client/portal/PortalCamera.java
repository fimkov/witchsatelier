package com.fimkov.witchsatelier.client.portal;

import com.fimkov.witchsatelier.windowway.PortalFrame;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PortalCamera extends Camera {
    void place(Vec3 pos, Quaternionf rot) {
        Vec3 fwd = toVec(new Vector3f(0, 0, -1).rotate(rot));
        float[] yp = PortalFrame.yawPitch(fwd);
        setRotation(yp[0], yp[1]);
        rotation().set(rot);
        getLookVector().set(0, 0, -1).rotate(rot);
        getUpVector().set(0, 1, 0).rotate(rot);
        getLeftVector().set(-1, 0, 0).rotate(rot);
        setPosition(pos);
    }

    private static final java.lang.reflect.Field EYE = field("eyeHeight"), EYE_OLD = field("eyeHeightOld");

    private static java.lang.reflect.Field field(String name) {
        try {
            java.lang.reflect.Field f = Camera.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Camera." + name, e);
        }
    }

    static void copyEyeHeight(Camera from, Camera to) {
        try {
            EYE.setFloat(to, EYE.getFloat(from));
            EYE_OLD.setFloat(to, EYE_OLD.getFloat(from));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Vec3 toVec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
