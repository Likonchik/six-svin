package ru.levin.protect;

import ru.levin.modules.misc.AntiScreenshot;

import java.awt.AWTPermission;
import java.security.Permission;

// Закрывает generic-уязвимость захвата ЭКРАНА через java.awt.Robot. Robot — JDK-класс, миксином его не
// перехватить. Но Robot.createScreenCapture() внутри проверяет AWTPermission("readDisplayPixels"), а
// конструктор Robot — AWTPermission("createRobot"). Ставим максимально пермиссивный SecurityManager,
// который РАЗРЕШАЕТ всё, КРОМЕ этих двух прав, и только пока антискринилка включена. Итог: ЛЮБОЙ мод
// (не только superbwarfare) не сможет снять экран через Robot.
//
// Требует JVM-флага -Djava.security.manager=allow (в dev он прописан в build.gradle). Без флага установка
// SecurityManager кидает исключение — мы его глушим и деградируем: остаются остальные слои (отмена
// обработчика superbwarfare, перехват takeScreenshot/grab, сетевой блок утечки кадра).
public final class RobotGuard extends SecurityManager {

    private static volatile boolean installed = false;

    public static void install() {
        if (installed) return;
        try {
            if (System.getSecurityManager() instanceof RobotGuard) {
                installed = true;
                return;
            }
            System.setSecurityManager(new RobotGuard());
            installed = true;
            System.out.println("[AntiScreenshot] RobotGuard installed (Robot screen capture is gated)");
        } catch (Throwable t) {
            // нет флага -Djava.security.manager=allow или среда запрещает SM — деградируем молча
            System.out.println("[AntiScreenshot] RobotGuard unavailable, relying on other layers (" + t + ")");
        }
    }

    @Override
    public void checkPermission(Permission perm) {
        if (perm instanceof AWTPermission) {
            String n = perm.getName();
            if (("readDisplayPixels".equals(n) || "createRobot".equals(n)) && AntiScreenshot.isActive()) {
                throw new SecurityException("OneTap AntiScreenshot: screen capture via java.awt.Robot blocked");
            }
        }
        // всё остальное разрешаем — нам нужен не песочница, а только AWT-блок захвата экрана
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        checkPermission(perm);
    }
}
