package fox;

import android.accessibilityservice.AccessibilityService;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.lang.reflect.Field;
import java.util.List;

public class Acc extends UiAutomatorTestCase {
    private UiAutomation ua() throws Exception {
        UiDevice d = getUiDevice();
        Field f = UiDevice.class.getDeclaredField("mUiAutomationBridge");
        f.setAccessible(true);
        Object bridge = f.get(d);
        Class<?> c = bridge.getClass();
        while (c != null) {
            try {
                Field mf = c.getDeclaredField("mUiAutomation");
                mf.setAccessible(true);
                return (UiAutomation) mf.get(bridge);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("mUiAutomation");
    }

    private boolean clickNode(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; cur != null && i < 8; i++) {
            if (cur.isClickable()) {
                System.out.println("CLICK node=" + cur.getClassName() + " text=" + cur.getText());
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            cur = cur.getParent();
        }
        System.out.println("CLICK no clickable ancestor");
        return false;
    }

    private boolean inY(AccessibilityNodeInfo n, Bundle b) {
        int miny = b.getInt("miny", -1);
        int maxy = b.getInt("maxy", -1);
        if (miny < 0 && maxy < 0) return true;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (miny >= 0 && r.top < miny) return false;
        if (maxy >= 0 && r.top > maxy) return false;
        return true;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findDesc(AccessibilityNodeInfo n, String desc, Bundle b) {
        if (n == null) return null;
        CharSequence d = n.getContentDescription();
        if (d != null && d.toString().contains(desc) && inY(n, b)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findDesc(n.getChild(i), desc, b);
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo n, String text, Bundle b) {
        if (n == null) return null;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String lc = text.toLowerCase();
        boolean hit = (t != null && t.toString().toLowerCase().contains(lc))
                || (d != null && d.toString().toLowerCase().contains(lc));
        if (hit && inY(n, b)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findText(n.getChild(i), text, b);
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isScrollable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private String textOfId(AccessibilityNodeInfo root, String id) {
        if (root == null) return "";
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
        if (l.isEmpty()) return "";
        CharSequence t = l.get(0).getText();
        return t == null ? "" : t.toString();
    }

    private void dump(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if ((t != null && t.length() > 0) || (d != null && d.length() > 0)) {
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            System.out.println("N " + n.getClassName() + " id=" + n.getViewIdResourceName()
                    + " text=" + t + " desc=" + d + " clickable=" + n.isClickable()
                    + " scrollable=" + n.isScrollable() + " editable=" + n.isEditable()
                    + " bounds=" + r);
        }
        for (int i = 0; i < n.getChildCount(); i++) dump(n.getChild(i), depth + 1);
    }

    public void testAcc() throws Exception {
        UiAutomation a = ua();
        Bundle b = getParams();
        String action = b != null ? b.getString("action", "dump") : "dump";
        AccessibilityNodeInfo root = a.getRootInActiveWindow();
        System.out.println("ACTION=" + action + " root=" + (root != null) + " ua=" + a + " windows=" + a.getWindows().size());
        for (int wi = 0; root == null && wi < 5; wi++) {
            sleep(500);
            root = a.getRootInActiveWindow();
            System.out.println("retry root=" + (root != null));
        }
        if ("dump".equals(action)) {
            dump(root, 0);
        } else if ("scroll".equals(action) || "scrollback".equals(action)) {
            AccessibilityNodeInfo s = findScrollable(root);
            System.out.println("scrollable=" + (s != null));
            if (s != null) System.out.println("scrollResult=" + s.performAction("scrollback".equals(action) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
        } else if ("clicktext".equals(action)) {
            String text = b.getString("text", "");
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
            System.out.println("found=" + list.size());
            boolean clicked = false;
            for (AccessibilityNodeInfo n : list) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                System.out.println("match class=" + n.getClassName() + " text=" + n.getText() + " clickable=" + n.isClickable() + " bounds=" + r);
                if (!clicked && inY(n, b) && clickNode(n)) clicked = true;
            }
            System.out.println("clickedAny=" + clicked);
        } else if ("clickdesc".equals(action)) {
            String desc = b.getString("desc", "");
            AccessibilityNodeInfo n = findDesc(root, desc, b);
            System.out.println("found=" + (n != null));
            if (n != null) System.out.println("clickedAny=" + clickNode(n));
        } else if ("back".equals(action)) {
            System.out.println("backResult=" + a.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
        } else if ("settext".equals(action)) {
            String text = b.getString("text", "");
            AccessibilityNodeInfo n = findEditable(root);
            System.out.println("editable=" + (n != null));
            if (n != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                System.out.println("setTextResult=" + n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
            }
        } else if ("clicktext2".equals(action)) {
            String text = b.getString("text", "");
            AccessibilityNodeInfo n = findText(root, text, b);
            System.out.println("found=" + (n != null));
            if (n != null) System.out.println("clickedAny=" + clickNode(n));
        } else if ("hastext".equals(action)) {
            String text = b.getString("text", "");
            List<AccessibilityNodeInfo> list = root == null ? null : root.findAccessibilityNodeInfosByText(text);
            System.out.println("found=" + (list == null ? 0 : list.size()));
        } else if ("pairinfo".equals(action)) {
            String code = textOfId(root, "com.android.settings:id/pairing_code");
            String ip = textOfId(root, "com.android.settings:id/ip_addr");
            System.out.println("CODE=" + code + " IP=" + ip);
        } else if ("clickwin".equals(action)) {
            String text = b.getString("text", "");
            String pkg = b.getString("pkg", "com.android.settings");
            boolean clicked = false;
            for (AccessibilityWindowInfo w : a.getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) continue;
                CharSequence rp = r.getPackageName();
                System.out.println("window pkg=" + rp + " type=" + w.getType());
                if (rp == null || !pkg.equals(rp.toString())) continue;
                List<AccessibilityNodeInfo> list = r.findAccessibilityNodeInfosByText(text);
                System.out.println("winFound=" + list.size());
                for (AccessibilityNodeInfo n : list) {
                    if (!clicked && clickNode(n)) clicked = true;
                }
            }
            System.out.println("winClickedAny=" + clicked);
        } else if ("settextid".equals(action)) {
            String id = b.getString("id", "");
            String text = b.getString("text", "");
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
            System.out.println("found=" + list.size());
            if (!list.isEmpty()) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                System.out.println("setTextResult=" + list.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
            }        } else if ("clickid".equals(action)) {
            String id = b.getString("id", "");
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
            System.out.println("found=" + list.size());
            for (AccessibilityNodeInfo n : list) {
                if (inY(n, b)) {
                    System.out.println("clickResult=" + clickNode(n));
                    break;
                }
            }
        }
        int settle = b != null ? b.getInt("settle", 250) : 250;
        if (settle > 0) sleep(settle);
    }
}
