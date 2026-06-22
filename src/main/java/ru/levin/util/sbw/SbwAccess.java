package ru.levin.util.sbw;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Грейсфул-доступ к сущностям SuperbWarfare / VVP (и любых аддонов на их фреймворке) БЕЗ жёсткой зависимости.
// Детект — по наличию методов/полей (рефлексия с кэшем), а не по имени мода. Подтверждено по jar 0.8.8:
//   - снаряды: пакет com.atsuishio.superbwarfare.entity.projectile.*; у FastThrowable-ветки publicе поля
//     explosionRadius/explosionDamage/gravity; у гранат приватный int fuse (тикает до 0 = взрыв).
//   - техника: имеет getOBBs():List<OBB> (SBW базовый VehicleEntity); VVP (tech.vvp.*) наследует его →
//     ловится тем же признаком. OBB = record(center, extents, rotation, part) c getVertices():Vector3d[8]
//     (мировые углы) и part():Part. getHealth()/getMaxHealth()/getEnergy() публичны.
public final class SbwAccess {

    private SbwAccess() {}

    private static final String PROJ_PKG = "com.atsuishio.superbwarfare.entity.projectile.";

    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Field MISSING_F;
    private static final Method MISSING_M;
    static {
        Field mf = null; Method mm = null;
        try { mf = SbwAccess.class.getDeclaredField("PROJ_PKG"); } catch (Throwable ignored) {}
        try { mm = SbwAccess.class.getDeclaredMethod("isProjectile", Entity.class); } catch (Throwable ignored) {}
        MISSING_F = mf; MISSING_M = mm;
    }

    // ===== снаряды =====
    public static boolean isProjectile(Entity e) {
        return e != null && e.getClass().getName().startsWith(PROJ_PKG);
    }

    public static boolean isExplosive(Entity e) {
        return isProjectile(e) && explosionRadius(e) > 0f;
    }

    public static boolean isGrenade(Entity e) {
        return fuse(e) >= 0;
    }

    // РГО — взрывается об удар (без отскока), в отличие от ручной M67
    public static boolean isRgo(Entity e) {
        return e != null && e.getClass().getSimpleName().contains("Rgo");
    }

    // ракета РПГ — gravity 0.015 + самоускорение ×1.03
    public static boolean isRpgRocket(Entity e) {
        if (e == null) return false;
        String n = e.getClass().getSimpleName();
        return n.contains("RpgRocket") || n.contains("MediumRocket");
    }

    public static float explosionRadius(Entity e) { return readFloat(e, "explosionRadius", 0f); }
    public static float explosionDamage(Entity e) { return readFloat(e, "explosionDamage", 0f); }
    public static float gravity(Entity e) { return readFloat(e, "gravity", 0.05f); }

    public static int fuse(Entity e) {
        Field f = field(e.getClass(), "fuse");
        if (f == null) return -1;
        try { return f.getInt(e); } catch (Throwable t) { return -1; }
    }

    public static String displayName(Entity e) {
        try { return e.getType().getDescription().getString(); }
        catch (Throwable t) { return e == null ? "?" : e.getClass().getSimpleName(); }
    }

    // ===== техника (SBW + VVP + аддоны) =====
    // признак техники = наличие метода getOBBs() (базовый VehicleEntity SBW; VVP наследует)
    public static boolean isVehicle(Entity e) {
        return e != null && method(e.getClass(), "getOBBs") != null;
    }

    // список OBB-частей техники (Object'ы класса OBB); пусто — если не техника/ошибка
    @SuppressWarnings("unchecked")
    public static List<Object> obbList(Entity e) {
        Method m = method(e.getClass(), "getOBBs");
        if (m == null) return Collections.emptyList();
        try {
            Object v = m.invoke(e);
            if (v instanceof List<?> l) return (List<Object>) l;
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    // 8 мировых углов одной OBB-части (через OBB.getVertices()); null при ошибке
    public static Vec3[] obbVertices(Object obb) {
        if (obb == null) return null;
        Method m = method(obb.getClass(), "getVertices");
        if (m == null) return null;
        try {
            Object res = m.invoke(obb);
            if (res instanceof Vector3d[] vs) {
                Vec3[] out = new Vec3[vs.length];
                for (int i = 0; i < vs.length; i++) out[i] = new Vec3(vs[i].x, vs[i].y, vs[i].z);
                return out;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // имя части OBB (TURRET/BODY/WHEEL_LEFT/...) или ""
    public static String obbPart(Object obb) {
        if (obb == null) return "";
        Method m = method(obb.getClass(), "part");
        if (m == null) return "";
        try { Object p = m.invoke(obb); return p == null ? "" : p.toString(); }
        catch (Throwable t) { return ""; }
    }

    public static float vehicleHealth(Entity e) { return invokeFloat(e, "getHealth", Float.NaN); }
    public static float vehicleMaxHealth(Entity e) { return invokeFloat(e, "getMaxHealth", Float.NaN); }

    public static int vehicleEnergy(Entity e) {
        Method m = method(e.getClass(), "getEnergy");
        if (m == null) return -1;
        try { Object v = m.invoke(e); return v instanceof Number ? ((Number) v).intValue() : -1; }
        catch (Throwable t) { return -1; }
    }

    // ===== низкоуровневое =====
    private static float readFloat(Entity e, String name, float def) {
        Field f = field(e.getClass(), name);
        if (f == null) return def;
        try { return f.getFloat(e); } catch (Throwable t) { return def; }
    }

    private static float invokeFloat(Entity e, String name, float def) {
        Method m = method(e.getClass(), name);
        if (m == null) return def;
        try { Object v = m.invoke(e); return v instanceof Number ? ((Number) v).floatValue() : def; }
        catch (Throwable t) { return def; }
    }

    private static Field field(Class<?> cls, String name) {
        if (cls == null) return null;
        String key = cls.getName() + "#" + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached == MISSING_F ? null : cached;
        Field found = null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); found = f; break; }
            catch (NoSuchFieldException ignored) {} catch (Throwable t) { break; }
        }
        FIELD_CACHE.put(key, found == null ? MISSING_F : found);
        return found;
    }

    private static Method method(Class<?> cls, String name) {
        if (cls == null) return null;
        String key = cls.getName() + "#" + name + "()";
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached == MISSING_M ? null : cached;
        Method found = null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name); m.setAccessible(true); found = m; break; }
            catch (NoSuchMethodException ignored) {} catch (Throwable t) { break; }
        }
        METHOD_CACHE.put(key, found == null ? MISSING_M : found);
        return found;
    }
}
